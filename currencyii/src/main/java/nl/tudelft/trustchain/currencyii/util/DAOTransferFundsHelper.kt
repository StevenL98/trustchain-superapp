package nl.tudelft.trustchain.currencyii.util

import android.util.Log
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.currencyii.CoinCommunity
import nl.tudelft.trustchain.currencyii.TrustChainHelper
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid
import nl.tudelft.trustchain.currencyii.sharedWallet.*
import nl.tudelft.trustchain.currencyii.util.taproot.CTransaction
import nl.tudelft.trustchain.currencyii.util.taproot.MuSig
import org.bitcoinj.core.Coin
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Transaction
import java.math.BigInteger
import java.util.concurrent.TimeUnit

class DAOTransferFundsHelper {
    private fun getTrustChainCommunity(): TrustChainCommunity {
        return IPv8Android.getInstance().getOverlay()
            ?: throw IllegalStateException("TrustChainCommunity is not configured")
    }

    private val trustchain: TrustChainHelper by lazy {
        TrustChainHelper(getTrustChainCommunity())
    }

    /**
     * 3.1 Send a proposal block on trustchain to ask for the signatures.
     * Assumed that people agreed to the transfer.
     */
    fun proposeTransferFunds(
        myPeer: Peer,
        mostRecentWallet: TrustChainBlock,
        receiverAddressSerialized: String,
        satoshiAmount: Long
    ): SWTransferFundsAskTransactionData {
        val walletData = SWJoinBlockTransactionData(mostRecentWallet.transaction).getData()
        val walletHash = mostRecentWallet.calculateHash().toHex()

        val total = walletData.SW_BITCOIN_PKS.size
        val requiredSignatures =
            SWUtil.percentageToIntThreshold(total, walletData.SW_VOTING_THRESHOLD)

        val proposalID = SWUtil.randomUUID()

        var askSignatureBlockData = SWTransferFundsAskTransactionData(
            walletData.SW_UNIQUE_ID,
            walletHash,
            requiredSignatures,
            satoshiAmount,
            walletData.SW_BITCOIN_PKS,
            receiverAddressSerialized,
            "",
            proposalID,
            walletData.SW_TRANSACTION_SERIALIZED
        )

        for (swParticipantPk in walletData.SW_TRUSTCHAIN_PKS) {
            Log.i(
                "Coin",
                "Sending TRANSFER proposal (total: ${walletData.SW_TRUSTCHAIN_PKS.size}) to $swParticipantPk"
            )
            askSignatureBlockData = SWTransferFundsAskTransactionData(
                walletData.SW_UNIQUE_ID,
                walletHash,
                requiredSignatures,
                satoshiAmount,

                walletData.SW_BITCOIN_PKS,
                receiverAddressSerialized,
                swParticipantPk,
                proposalID,
                walletData.SW_TRANSACTION_SERIALIZED
            )

            trustchain.createProposalBlock(
                askSignatureBlockData.getJsonString(),
                myPeer.publicKey.keyToBin(),
                askSignatureBlockData.blockType
            )
        }
        return askSignatureBlockData
    }

    /**
     * 3.2 Transfer funds from an existing shared wallet to a third-party. Broadcast bitcoin transaction.
     */
    fun transferFunds(
        myPeer: Peer,
        walletBlockData: TrustChainTransaction,
        blockData: SWTransferFundsAskBlockTD,
        signatures: List<String>,
        receiverAddress: String,
        paymentAmount: Long
    ) {
        val oldWalletBlockData = SWTransferDoneTransactionData(walletBlockData)
        val newTransactionSerialized = blockData.SW_TRANSACTION_SERIALIZED

        val walletManager = WalletManagerAndroid.getInstance()

        val signaturesOfOldOwners = signatures.map {
            BigInteger(1, it.hexToBytes())
        }

        val noncePoints = oldWalletBlockData.getData().SW_NONCE_PKS.map {
            ECKey.fromPublicOnly(it.hexToBytes())
        }

        val (aggregateNoncePoint, _) = MuSig.aggregate_schnorr_nonces(noncePoints)

        val newTransactionProposal = newTransactionSerialized.hexToBytes()
        val (status, serializedTransaction) = walletManager.safeSendingTransactionFromMultiSig(
            signaturesOfOldOwners,
            aggregateNoncePoint,
            CTransaction().deserialize(newTransactionProposal),
            org.bitcoinj.core.Address.fromString(walletManager.params, receiverAddress),
            paymentAmount
        )

        if (status) {
            Log.i("Coin", "succesfully submitted taproot transaction to server")
        } else {
            Log.e("Coin", "taproot transaction submission to server failed")
        }

        broadcastTransferFundSuccessful(myPeer, oldWalletBlockData, serializedTransaction)
    }

    /**
     * 3.3 Everything is done, publish the final serialized bitcoin transaction data on trustchain.
     */
    private fun broadcastTransferFundSuccessful(
        myPeer: Peer,
        oldBlockData: SWTransferDoneTransactionData,
        serializedTransaction: String
    ) {
        val newData = SWTransferDoneTransactionData(oldBlockData.jsonData)
        val walletManager = WalletManagerAndroid.getInstance()

        newData.addBitcoinPk(walletManager.networkPublicECKeyHex())
        newData.addTrustChainPk(myPeer.publicKey.keyToBin().toHex())
        newData.setTransactionSerialized(serializedTransaction)
        newData.addNoncePk(walletManager.nonceECPointHex())

        trustchain.createProposalBlock(
            newData.getJsonString(),
            myPeer.publicKey.keyToBin(),
            newData.blockType
        )
    }

    companion object {
        /**
         * Given a shared wallet transfer fund proposal block, calculate the signature and send an agreement block.
         */
        fun transferFundsBlockReceived(
            oldTransactionSerialized: String,
            block: TrustChainBlock,
            transferBlock: SWTransferDoneBlockTD,
            myPublicKey: ByteArray,
            votedInFavor: Boolean
        ) {
            val trustchain = TrustChainHelper(IPv8Android.getInstance().getOverlay() ?: return)
            val blockData = SWTransferFundsAskTransactionData(block.transaction).getData()

            Log.i("Coin","Signature request for transfer funds: ${blockData.SW_RECEIVER_PK}, me: ${myPublicKey.toHex()}")

            if (blockData.SW_RECEIVER_PK != myPublicKey.toHex()) {
                return
            }

            Log.i("Coin", "Signing transfer funds transaction: $blockData")

            val walletManager = WalletManagerAndroid.getInstance()

            val newTransactionSerialized = blockData.SW_TRANSACTION_SERIALIZED
            val signature = walletManager.safeSigningTransactionFromMultiSig(
                CTransaction().deserialize(oldTransactionSerialized.hexToBytes()),
                CTransaction().deserialize(newTransactionSerialized.hexToBytes()),
                transferBlock.SW_BITCOIN_PKS.map { ECKey.fromPublicOnly(it.hexToBytes()) },
                transferBlock.SW_NONCE_PKS.map { ECKey.fromPublicOnly(it.hexToBytes()) },
                walletManager.protocolECKey(),
                org.bitcoinj.core.Address.fromString(walletManager.params, blockData.SW_TRANSFER_FUNDS_TARGET_SERIALIZED),
                blockData.SW_TRANSFER_FUNDS_AMOUNT
            )

            val signatureSerialized = signature.toByteArray().toHex()

            if (votedInFavor) {
                val agreementData = SWResponseSignatureTransactionData(
                    blockData.SW_UNIQUE_ID,
                    blockData.SW_UNIQUE_PROPOSAL_ID,
                    signatureSerialized
                )

                trustchain.createProposalBlock(
                    agreementData.getTransactionData(),
                    myPublicKey,
                    agreementData.blockType
                )
            } else {
                val negativeResponseData = SWResponseNegativeSignatureTransactionData(
                    blockData.SW_UNIQUE_ID,
                    blockData.SW_UNIQUE_PROPOSAL_ID,
                    signatureSerialized
                )

                trustchain.createProposalBlock(
                    negativeResponseData.getTransactionData(),
                    myPublicKey,
                    negativeResponseData.blockType
                )
            }
        }
    }
}
