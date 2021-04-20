package nl.tudelft.trustchain.currencyii.util

import android.content.Context
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.currencyii.CoinCommunity
import nl.tudelft.trustchain.currencyii.TrustChainHelper
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid
import nl.tudelft.trustchain.currencyii.sharedWallet.SWJoinBlockTransactionData
import org.bitcoinj.core.Coin
import java.util.concurrent.TimeUnit

class DAOCreateHelper {
    private fun getTrustChainCommunity(): TrustChainCommunity {

        return IPv8Android.getInstance().getOverlay()
            ?: throw IllegalStateException("TrustChainCommunity is not configured")
    }

    private val trustchain: TrustChainHelper by lazy {
        TrustChainHelper(getTrustChainCommunity())
    }

    /**
     * 1.1 Create a shared wallet block.
     * The bitcoin transaction may take some time to finish.
     * If the transaction     private fun getTrustChainCommunity(): TrustChainCommunity {
    return IPv8Android.getInstance().getOverlay()
    ?: throw IllegalStateException("TrustChainCommunity is not configured")
    }

    private val trustchain: TrustChainHelper by lazy {
    TrustChainHelper(getTrustChainCommunity())
    }is valid, the result is broadcasted on trust chain.
     * **Throws** exceptions if something goes wrong with creating or broadcasting bitcoin transaction.
     */
    fun createBitcoinGenesisWallet(
        myPeer: Peer,
        entranceFee: Long,
        threshold: Int
    ) {
        val walletManager = WalletManagerAndroid.getInstance()
        val (success, serializedTransaction) = walletManager.safeCreationAndSendGenesisWallet(
            Coin.valueOf(entranceFee)
        )

        print(success)
        //todo do something if success = false

        // Broadcast on trust chain if no errors are thrown in the previous step.
        broadcastCreatedSharedWallet(
            myPeer,
            serializedTransaction,
            entranceFee,
            threshold
        )
    }

    /**
     * 1.2 Finishes the last step of creating a genesis shared bitcoin wallet.
     * Posts a self-signed trust chain block containing the shared wallet data.
     */
    private fun broadcastCreatedSharedWallet(
        myPeer: Peer,
        transactionSerialized: String,
        entranceFee: Long,
        votingThreshold: Int
    ) {
        val walletManager = WalletManagerAndroid.getInstance()
        val bitcoinPublicKey = walletManager.networkPublicECKeyHex()
        val trustChainPk = myPeer.publicKey.keyToBin()
        val noncePoint = walletManager.nonceECPointHex()

        val blockData = SWJoinBlockTransactionData(
            entranceFee,
            transactionSerialized,
            votingThreshold,
            arrayListOf(trustChainPk.toHex()),
            arrayListOf(bitcoinPublicKey),
            arrayListOf(noncePoint)
        )

        trustchain.createProposalBlock(blockData.getJsonString(), trustChainPk, blockData.blockType)
    }
}
