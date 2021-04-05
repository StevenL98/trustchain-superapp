package nl.tudelft.trustchain.currencyii

import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.currencyii.coin.WalletManagerAndroid
import nl.tudelft.trustchain.currencyii.sharedWallet.*
import nl.tudelft.trustchain.currencyii.util.DAOCreateHelper
import nl.tudelft.trustchain.currencyii.util.DAOJoinHelper
import nl.tudelft.trustchain.currencyii.util.DAOTransferFundsHelper
import nl.tudelft.trustchain.currencyii.util.taproot.CTransaction
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.Transaction
import java.math.BigInteger

@Suppress("UNCHECKED_CAST")
class CoinCommunity : Community() {
    override val serviceId = "02313685c1912a141279f8248fc8db5899c5df5b"

    private fun getTrustChainCommunity(): TrustChainCommunity {
        return IPv8Android.getInstance().getOverlay()
            ?: throw IllegalStateException("TrustChainCommunity is not configured")
    }

    private val daoCreateHelper = DAOCreateHelper()
    private val daoJoinHelper = DAOJoinHelper()
    private val daoTransferFundsHelper = DAOTransferFundsHelper()

    /**
     * Create a bitcoin genesis wallet and broadcast the result on trust chain.
     * The bitcoin transaction may take some time to finish.
     * **Throws** exceptions if something goes wrong with creating or broadcasting bitcoin transaction.
     */
    fun createBitcoinGenesisWallet(
        entranceFee: Long,
        threshold: Int,
        progressCallback: ((progress: Double) -> Unit)? = null,
        timeout: Long = DEFAULT_BITCOIN_MAX_TIMEOUT
    ) {
        daoCreateHelper.createBitcoinGenesisWallet(
            myPeer,
            entranceFee,
            threshold,
            progressCallback,
            timeout
        )
    }

    /**
     * 2.1 Send a proposal on the trust chain to join a shared wallet and to collect signatures.
     * The proposal is a serialized bitcoin join transaction.
     * **NOTE** the latest walletBlockData should be given, otherwise the serialized transaction is invalid.
     * @param walletBlock - the latest (that you know of) shared wallet block.
     */
    fun proposeJoinWallet(
        walletBlock: TrustChainBlock
    ): SWSignatureAskTransactionData {
        return daoJoinHelper.proposeJoinWallet(myPeer, walletBlock)
    }

    /**
     * 2.2 Commit the join wallet transaction on the bitcoin blockchain and broadcast the result on trust chain.
     *
     * Note:
     * There should be enough sufficient signatures, based on the multisig wallet data.
     * **Throws** exceptions if something goes wrong with creating or broadcasting bitcoin transaction.
     */
    fun joinBitcoinWallet(
        walletBlockData: TrustChainTransaction,
        blockData: SWSignatureAskBlockTD,
        signatures: List<String>
    ) {
        daoJoinHelper.joinBitcoinWallet(
            myPeer,
            walletBlockData,
            blockData,
            signatures
        )
    }

    /**
     * 3.1 Send a proposal block on trustchain to ask for the signatures.
     * Assumed that people agreed to the transfer.
     */
    fun proposeTransferFunds(
        mostRecentWallet: TrustChainBlock,
        receiverAddressSerialized: String,
        satoshiAmount: Long
    ): SWTransferFundsAskTransactionData {
        return daoTransferFundsHelper.proposeTransferFunds(
            myPeer,
            mostRecentWallet,
            receiverAddressSerialized,
            satoshiAmount
        )
    }

    /**
     * 3.2 Transfer funds from an existing shared wallet to a third-party. Broadcast bitcoin transaction.
     */
    fun transferFunds(
        transferFundsData: SWTransferFundsAskTransactionData,
        walletData: SWJoinBlockTD,
        serializedSignatures: List<String>,
        receiverAddress: String,
        satoshiAmount: Long,
        progressCallback: ((progress: Double) -> Unit)? = null,
        timeout: Long = DEFAULT_BITCOIN_MAX_TIMEOUT
    ) {
        daoTransferFundsHelper.transferFunds(
            myPeer,
            transferFundsData,
            walletData,
            serializedSignatures,
            receiverAddress,
            satoshiAmount,
            progressCallback,
            timeout
        )
    }

    /**
     * Discover shared wallets that you can join, return the latest blocks that the user knows of.
     */
    fun discoverSharedWallets(): List<TrustChainBlock> {
        val swBlocks = getTrustChainCommunity().database.getBlocksWithType(JOIN_BLOCK)
        return swBlocks
            .distinctBy { SWJoinBlockTransactionData(it.transaction).getData().SW_UNIQUE_ID }
            .map { fetchLatestSharedWalletBlock(it, swBlocks) ?: it }
    }

    /**
     * Discover shared wallets that you can join, return the latest (known) blocks
     * Fetch the latest block associated with a shared wallet.
     * swBlockHash - the hash of one of the blocks associated with a shared wallet.
     */
    fun fetchLatestSharedWalletBlock(swBlockHash: ByteArray): TrustChainBlock? {
        val swBlock = getTrustChainCommunity().database.getBlockWithHash(swBlockHash)
            ?: return null
        val swBlocks = getTrustChainCommunity().database.getBlocksWithType(JOIN_BLOCK)
        return fetchLatestSharedWalletBlock(swBlock, swBlocks)
    }

    /**
     * Fetch the latest shared wallet block, based on a given block 'block'.
     * The unique shared wallet id is used to find the most recent block in
     * the 'sharedWalletBlocks' list.
     */
    private fun fetchLatestSharedWalletBlock(
        block: TrustChainBlock,
        fromBlocks: List<TrustChainBlock>
    ): TrustChainBlock? {
        if (block.type != JOIN_BLOCK) {
            return null
        }
        val walletId = SWJoinBlockTransactionData(block.transaction).getData().SW_UNIQUE_ID

        return fromBlocks
            .filter { it.type == JOIN_BLOCK } // make sure the blocks have the correct type!
            .filter { SWJoinBlockTransactionData(it.transaction).getData().SW_UNIQUE_ID == walletId }
            .maxBy { it.timestamp.time }
    }

    /**
     * Fetch the shared wallet blocks that you are part of, based on your trustchain PK.
     */
    fun fetchLatestJoinedSharedWalletBlocks(): List<TrustChainBlock> {
        return discoverSharedWallets().filter {
            val blockData = SWJoinBlockTransactionData(it.transaction).getData()
            val userTrustchainPks = blockData.SW_TRUSTCHAIN_PKS
            userTrustchainPks.contains(myPeer.publicKey.keyToBin().toHex())
        }
    }

    private fun fetchSignatureRequestReceiver(block: TrustChainBlock): String {
        if (block.type == SIGNATURE_ASK_BLOCK) {
            return SWSignatureAskTransactionData(block.transaction).getData().SW_RECEIVER_PK
        }

        if (block.type == TRANSFER_FUNDS_ASK_BLOCK) {
            return SWTransferFundsAskTransactionData(block.transaction).getData().SW_RECEIVER_PK
        }

        return "invalid-pk"
    }

    fun fetchSignatureRequestProposalId(block: TrustChainBlock): String {
        if (block.type == SIGNATURE_ASK_BLOCK) {
            return SWSignatureAskTransactionData(block.transaction).getData().SW_UNIQUE_PROPOSAL_ID
        }
        if (block.type == TRANSFER_FUNDS_ASK_BLOCK) {
            return SWTransferFundsAskTransactionData(block.transaction).getData()
                .SW_UNIQUE_PROPOSAL_ID
        }

        return "invalid-proposal-id"
    }

    /**
     * Fetch all join and transfer proposals in descending timestamp order.
     * Speed assumption: each proposal has a unique proposal ID (distinct by unique proposal id,
     * without taking the unique wallet id into account).
     */
    fun fetchProposalBlocks(): List<TrustChainBlock> {
        val joinProposals = getTrustChainCommunity().database.getBlocksWithType(SIGNATURE_ASK_BLOCK)
        val transferProposals = getTrustChainCommunity().database.getBlocksWithType(
            TRANSFER_FUNDS_ASK_BLOCK
        )
        return joinProposals
            .union(transferProposals)
            .filter {
                fetchSignatureRequestReceiver(it) == myPeer.publicKey.keyToBin()
                    .toHex() && !checkEnoughFavorSignatures(it)
            }
            .distinctBy { fetchSignatureRequestProposalId(it) }
            .sortedByDescending { it.timestamp }
    }

    /**
     * Fetch all DAO blocks that contain a signature. These blocks are the response of a signature request.
     * Signatures are fetched from [SIGNATURE_AGREEMENT_BLOCK] type blocks.
     */
    fun fetchProposalSignatures(walletId: String, proposalId: String): List<String> {
        return getTrustChainCommunity().database.getBlocksWithType(SIGNATURE_AGREEMENT_BLOCK)
            .filter {
                val blockData = SWResponseSignatureTransactionData(it.transaction)
                blockData.matchesProposal(walletId, proposalId)
            }.map {
                val blockData = SWResponseSignatureTransactionData(it.transaction).getData()
                blockData.SW_SIGNATURE_SERIALIZED
            }
    }

    /**
     * Fetch all DAO blocks that contain a negative signature. These blocks are the response of a negative signature request.
     * Signatures are fetched from [SIGNATURE_AGREEMENT_NEGATIVE_BLOCK] type blocks.
     */
    fun fetchNegativeProposalSignatures(walletId: String, proposalId: String): List<String> {
        return getTrustChainCommunity().database.getBlocksWithType(
            SIGNATURE_AGREEMENT_NEGATIVE_BLOCK
        )
            .filter {
                val blockData = SWResponseNegativeSignatureTransactionData(it.transaction)
                blockData.matchesProposal(walletId, proposalId)
            }.map {
                val blockData = SWResponseNegativeSignatureTransactionData(it.transaction).getData()
                blockData.SW_SIGNATURE_SERIALIZED
            }
    }

    /**
     * Given a shared wallet proposal block, calculate the signature and respond with a trust chain block.
     */
    fun joinAskBlockReceived(
        block: TrustChainBlock,
        myPublicKey: ByteArray,
        votedInFavor: Boolean
    ) {
        val latestHash = SWSignatureAskTransactionData(block.transaction).getData()
            .SW_PREVIOUS_BLOCK_HASH
        val mostRecentSWBlock = fetchLatestSharedWalletBlock(latestHash.hexToBytes())
            ?: throw IllegalStateException("Most recent DAO block not found")
        val joinBlock = SWJoinBlockTransactionData(mostRecentSWBlock.transaction).getData()
        val oldTransaction = joinBlock.SW_TRANSACTION_SERIALIZED

        DAOJoinHelper.joinAskBlockReceived(oldTransaction, block, joinBlock, myPublicKey, votedInFavor)
    }

    /**
     * Given a shared wallet transfer fund proposal block, calculate the signature and respond with a trust chain block.
     */
    fun transferFundsBlockReceived(
        block: TrustChainBlock,
        myPublicKey: ByteArray,
        votedInFavor: Boolean
    ) {
        val latestHash = SWTransferFundsAskTransactionData(block.transaction).getData()
            .SW_PREVIOUS_BLOCK_HASH
        val mostRecentSWBlock = fetchLatestSharedWalletBlock(latestHash.hexToBytes())
            ?: throw IllegalStateException("Most recent DAO block not found")
        val oldTransaction = SWJoinBlockTransactionData(mostRecentSWBlock.transaction).getData()
            .SW_TRANSACTION_SERIALIZED

        DAOTransferFundsHelper.transferFundsBlockReceived(
            oldTransaction,
            block,
            myPublicKey,
            votedInFavor
        )
    }

    /**
     * Given a proposal, check if the number of signatures required is met
     */
    fun checkEnoughFavorSignatures(block: TrustChainBlock): Boolean {
        if (block.type == SIGNATURE_ASK_BLOCK) {
            val data = SWSignatureAskTransactionData(block.transaction).getData()
            val signatures =
                ArrayList(
                    fetchProposalSignatures(
                        data.SW_UNIQUE_ID,
                        data.SW_UNIQUE_PROPOSAL_ID
                    )
                )
            return data.SW_SIGNATURES_REQUIRED <= signatures.size
        }
        if (block.type == TRANSFER_FUNDS_ASK_BLOCK) {
            val data = SWTransferFundsAskTransactionData(block.transaction).getData()
            val signatures =
                ArrayList(
                    fetchProposalSignatures(
                        data.SW_UNIQUE_ID,
                        data.SW_UNIQUE_PROPOSAL_ID
                    )
                )
            return data.SW_SIGNATURES_REQUIRED <= signatures.size
        }

        return false
    }

    /**
     * Check if the number of required votes are more than the number of possible votes minus the negative votes.
     */
    fun canWinJoinRequest(data: SWSignatureAskBlockTD): Boolean {
        val sw =
            discoverSharedWallets().filter { b -> SWJoinBlockTransactionData(b.transaction).getData().SW_UNIQUE_ID == data.SW_UNIQUE_ID }[0]
        val swData = SWJoinBlockTransactionData(sw.transaction).getData()
        val againstSignatures = ArrayList(
            fetchNegativeProposalSignatures(
                data.SW_UNIQUE_ID,
                data.SW_UNIQUE_PROPOSAL_ID
            )
        )
        val totalVoters = swData.SW_BITCOIN_PKS
        val requiredVotes = data.SW_SIGNATURES_REQUIRED

        return requiredVotes <= totalVoters.size - againstSignatures.size
    }

    /**
     * Get my signature to check if I already voted
     */
    fun getMySignatureJoinRequest(data: SWSignatureAskBlockTD): BigInteger {
        val walletManager = WalletManagerAndroid.getInstance()

        val latestHash = data.SW_PREVIOUS_BLOCK_HASH
        val mostRecentSWBlock =
            fetchLatestSharedWalletBlock(latestHash.hexToBytes())
                ?: throw IllegalStateException("Most recent DAO block not found")
        val joinBlock = SWJoinBlockTransactionData(mostRecentSWBlock.transaction).getData()
        val oldTransaction = joinBlock.SW_TRANSACTION_SERIALIZED

        val newTransactionSerialized = data.SW_TRANSACTION_SERIALIZED
        return walletManager.safeSigningJoinWalletTransaction(
            Transaction(walletManager.params, oldTransaction.hexToBytes()),
            CTransaction().deserialize(newTransactionSerialized.hexToBytes()),
            joinBlock.SW_BITCOIN_PKS.map { ECKey.fromPublicOnly(it.hexToBytes()) },
            joinBlock.SW_NONCE_PKS.map { ECKey.fromPublicOnly(it.hexToBytes()) },
            walletManager.protocolECKey()
        )
    }

    /**
     * Get my signature to check if I already voted
     */
    fun getMySignatureTransaction(data: SWTransferFundsAskBlockTD): ECKey.ECDSASignature {
        val walletManager = WalletManagerAndroid.getInstance()

        val latestHash = data.SW_PREVIOUS_BLOCK_HASH
        val mostRecentSWBlock =
            fetchLatestSharedWalletBlock(latestHash.hexToBytes())
                ?: throw IllegalStateException("Most recent DAO block not found")
        val oldTransaction = SWJoinBlockTransactionData(mostRecentSWBlock.transaction).getData()
            .SW_TRANSACTION_SERIALIZED

        val satoshiAmount = Coin.valueOf(data.SW_TRANSFER_FUNDS_AMOUNT)
        val previousTransaction = Transaction(
            walletManager.params,
            oldTransaction.hexToBytes()
        )
        val receiverAddress = Address.fromString(
            walletManager.params,
            data.SW_TRANSFER_FUNDS_TARGET_SERIALIZED
        )
        return walletManager.safeSigningTransactionFromMultiSig(
            previousTransaction,
            walletManager.protocolECKey(),
            receiverAddress,
            satoshiAmount
        )
    }

    /**
     * Check if the number of required votes are more than the number of possible votes minus the negative votes.
     */
    fun canWinTransferRequest(data: SWTransferFundsAskBlockTD): Boolean {
        val againstSignatures = ArrayList(
            fetchNegativeProposalSignatures(
                data.SW_UNIQUE_ID,
                data.SW_UNIQUE_PROPOSAL_ID
            )
        )
        val totalVoters = data.SW_BITCOIN_PKS
        val requiredVotes = data.SW_SIGNATURES_REQUIRED

        return requiredVotes <= totalVoters.size - againstSignatures.size
    }

    companion object {
        /**
         * Helper method that serializes a bitcoin transaction to a string.
         */
        fun getSerializedTransaction(transaction: Transaction): String {
            return transaction.bitcoinSerialize().toHex()
        }

        // Default maximum wait timeout for bitcoin transaction broadcasts in seconds
        const val DEFAULT_BITCOIN_MAX_TIMEOUT: Long = 60 * 5

        // Block type for join DAO blocks
        const val JOIN_BLOCK = "v1DAO_JOIN"

        // Block type for transfer funds (from a DAO)
        const val TRANSFER_FINAL_BLOCK = "v1DAO_TRANSFER_FINAL"

        // Block type for basic signature requests
        const val SIGNATURE_ASK_BLOCK = "v1DAO_ASK_SIGNATURE"

        const val NONCE_ASK_BLOCK = "v1DAO_ASK_NONCE"

        // Block type for transfer funds signature requests
        const val TRANSFER_FUNDS_ASK_BLOCK = "v1DAO_TRANSFER_ASK_SIGNATURE"

        // Block type for responding to a signature request with a (should be valid) signature
        const val SIGNATURE_AGREEMENT_BLOCK = "v1DAO_SIGNATURE_AGREEMENT"

        const val NONCE_AGREEMENT_BLOCK = "v1DAO_NONCE_AGREEMENT"

        // Block type for responding with a negative vote to a signature request with a signature
        const val SIGNATURE_AGREEMENT_NEGATIVE_BLOCK = "v1DAO_SIGNATURE_AGREEMENT_NEGATIVE"
    }
}
