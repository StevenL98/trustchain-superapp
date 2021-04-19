package nl.tudelft.trustchain.currencyii.coin

import android.content.Context
import android.util.Log
import com.google.common.base.Joiner
import com.google.gson.JsonParser
import info.blockchain.api.APIException
import info.blockchain.api.blockexplorer.BlockExplorer
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.currencyii.CoinCommunity
import nl.tudelft.trustchain.currencyii.NONCE_KEY
import nl.tudelft.trustchain.currencyii.util.taproot.*
import nl.tudelft.trustchain.currencyii.util.taproot.Address
import org.bitcoinj.core.*
import org.bitcoinj.core.DumpedPrivateKey
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.crypto.DeterministicKey
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.RegTestParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.script.Script
import org.bitcoinj.script.ScriptBuilder
import org.bitcoinj.script.ScriptPattern
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.KeyChainGroup
import org.bitcoinj.wallet.KeyChainGroupStructure
import org.bitcoinj.wallet.SendRequest
import org.bouncycastle.math.ec.ECPoint
import java.io.File
import java.math.BigInteger
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.experimental.and

const val TEST_NET_WALLET_NAME = "forwarding-service-testnet"
const val REG_TEST_WALLET_NAME = "forwarding-service-regtest"
const val MAIN_NET_WALLET_NAME = "forwarding-service"
const val MIN_BLOCKCHAIN_PEERS_TEST_NET = 5
const val MIN_BLOCKCHAIN_PEERS_REG_TEST = 1
const val MIN_BLOCKCHAIN_PEERS_PRODUCTION = 5
const val REG_TEST_FAUCET_IP = "131.180.27.224"
// const val REG_TEST_FAUCET_DOMAIN = "taproot.tribler.org"

var MIN_BLOCKCHAIN_PEERS = MIN_BLOCKCHAIN_PEERS_TEST_NET

/**
 * The wallet manager which encapsulates the functionality of all possible interactions
 * with bitcoin wallets (including multi-signature wallets).
 * NOTE: Ideally should be separated from any Android UI concepts. Not the case currently.
 */
@Suppress("DEPRECATION", "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class WalletManager(
    walletManagerConfiguration: WalletManagerConfiguration,
    walletDir: File,
    serializedDeterministicKey: SerializedDeterministicKey? = null,
    addressPrivateKeyPair: AddressPrivateKeyPair? = null
) {
    val kit: WalletAppKit
    val params: NetworkParameters
    var isDownloading: Boolean = true
    var progress: Int = 0
    val key = addressPrivateKeyPair

    /**
     * Initializes WalletManager.
     */
    init {
        Log.i("Coin", "Coin: WalletManager attempting to start.")

        params = when (walletManagerConfiguration.network) {
            BitcoinNetworkOptions.TEST_NET -> TestNet3Params.get()
            BitcoinNetworkOptions.PRODUCTION -> MainNetParams.get()
            BitcoinNetworkOptions.REG_TEST -> RegTestParams.get()
        }

        val filePrefix = when (walletManagerConfiguration.network) {
            BitcoinNetworkOptions.TEST_NET -> TEST_NET_WALLET_NAME
            BitcoinNetworkOptions.PRODUCTION -> MAIN_NET_WALLET_NAME
            BitcoinNetworkOptions.REG_TEST -> REG_TEST_WALLET_NAME
        }

        kit = object : WalletAppKit(params, walletDir, filePrefix) {
            override fun onSetupCompleted() {
                // Make a fresh new key if no keys in stored wallet.
                if (wallet().keyChainGroupSize < 1) {
                    Log.i("Coin", "Coin: Added manually created fresh key")
                    wallet().importKey(ECKey())
                }
                wallet().allowSpendingUnconfirmedTransactions()
                Log.i("Coin", "Coin: WalletManager started successfully.")
            }
        }

        MIN_BLOCKCHAIN_PEERS = when (params) {
            RegTestParams.get() -> MIN_BLOCKCHAIN_PEERS_REG_TEST
            MainNetParams.get() -> MIN_BLOCKCHAIN_PEERS_PRODUCTION
            TestNet3Params.get() -> MIN_BLOCKCHAIN_PEERS_TEST_NET
            else -> MIN_BLOCKCHAIN_PEERS
        }

        if (params == RegTestParams.get()) {
            try {
                val localHost = InetAddress.getByName(REG_TEST_FAUCET_IP)
                kit.setPeerNodes(PeerAddress(params, localHost, params.port))
            } catch (e: UnknownHostException) {
                throw RuntimeException(e)
            }
        }

        if (serializedDeterministicKey != null) {
            Log.i(
                "Coin",
                "Coin: received a key to import, will clear the wallet and download again."
            )
            val deterministicSeed = DeterministicSeed(
                serializedDeterministicKey.seed,
                null,
                "",
                serializedDeterministicKey.creationTime
            )
            kit.restoreWalletFromSeed(deterministicSeed)
        }

        kit.setDownloadListener(object : DownloadProgressTracker() {
            override fun progress(
                pct: Double,
                blocksSoFar: Int,
                date: Date?
            ) {
                super.progress(pct, blocksSoFar, date)
                val percentage = pct.toInt()
                progress = percentage
                println("Progress: $percentage")
                Log.i("Coin", "Progress: $percentage")
            }

            override fun doneDownload() {
                super.doneDownload()
                progress = 100
                Log.i("Coin", "Download Complete!")
                Log.i("Coin", "Balance: ${kit.wallet().balance}")
                isDownloading = false
            }
        })

        Log.i("Coin", "Coin: starting the setup of kit.")
        kit.setBlockingStartup(false)
            .startAsync()
            .awaitRunning()

        if (addressPrivateKeyPair != null) {
            Log.i(
                "Coin",
                "Coin: Importing Address: ${addressPrivateKeyPair.address}, " +
                    "with SK: ${addressPrivateKeyPair.privateKey}"
            )

            val privateKey = addressPrivateKeyPair.privateKey
            val key = formatKey(privateKey)

            Log.i(
                "Coin",
                "Coin: Address from private key is: " + LegacyAddress.fromKey(
                    params,
                    key
                ).toString()
            )

            kit.wallet().importKey(key)
        }
        Log.i("Coin", "Coin: finished the setup of kit.")
        Log.i("Coin", "Coin: Imported Keys: ${kit.wallet().importedKeys}")
        Log.i("Coin", "Coin: Imported Keys: ${kit.wallet().toString(true, false, false, null)}")
    }

    private fun formatKey(privateKey: String): ECKey {
        return if (privateKey.length == 51 || privateKey.length == 52) {
            val dumpedPrivateKey =
                DumpedPrivateKey.fromBase58(params, privateKey)
            dumpedPrivateKey.key
        } else {
            val bigIntegerPrivateKey = Base58.decodeToBigInteger(privateKey)
            ECKey.fromPrivate(bigIntegerPrivateKey)
        }
    }

    /**
     * Returns our bitcoin address we use in all multi-sig contracts
     * we are part of.
     * @return hex representation of our address
     */
    fun protocolAddress(): org.bitcoinj.core.Address {
        return kit.wallet().issuedReceiveAddresses[0]
    }

    /**
     * Returns our bitcoin public key we use in all multi-sig contracts
     * we are part of.
     * @return hex representation of our public key (this is not an address)
     */
    fun protocolECKey(): ECKey {
        return kit.wallet().issuedReceiveKeys[0]
    }

    /**
     * Returns our bitcoin public key (in hex) we use in all multi-sig contracts
     * we are part of.
     * @return hex representation of our public key (this is not an address)
     */
    fun networkPublicECKeyHex(): String {
        return protocolECKey().publicKeyAsHex
    }

    fun nonceECPointHex(): String {
        return NONCE_KEY.second.getEncoded(true).toHex()
    }

    /**
     * (1) When you are creating a multi-sig wallet for yourself alone
     * as the genesis (wallet).
     *
     * Calculates the MuSig address and sends the entrance fee to it.
     *
     * @param entranceFee the entrance fee you are sending
     * @return TransactionPackage
     */
    fun safeCreationAndSendGenesisWallet(
        entranceFee: Coin
    ): TransactionBroadcast {
        Log.i("Coin", "Coin: (safeCreationAndSendGenesisWallet start).")

        Log.i("Coin", "Coin: we are making a new genesis wallet for us alone.")

        Log.i("Coin", "Coin: we will now make a 1/1 wallet")

        val (_, aggPubKey) = MuSig.generate_musig_key(listOf(protocolECKey()))

        val pubKeyDataMusig = aggPubKey.getEncoded(true)
        val programMusig = byteArrayOf(pubKeyDataMusig[0] and 1.toByte()).plus(
            pubKeyDataMusig.drop(
                1
            )
        ).toHex()
        val version = 1
        val addressMuSig = Address.program_to_witness(version, programMusig.hexToBytes())

        val transaction = Transaction(params)
        // Add an output with the entrance fee & MuSig address.
        transaction.addOutput(
            entranceFee, org.bitcoinj.core.Address.fromString(
                params,
                addressMuSig
            )
        )

        Log.i("Coin", "Coin: use SendRequest to add our entranceFee input & change address.")

        val req = SendRequest.forTx(transaction)
        req.changeAddress = protocolAddress()
        kit.wallet().completeTx(req)

        Log.i("YEET", "txid: " + req.tx.txId.toString())
        Log.i("YEET", "serialized tx: " + req.tx.bitcoinSerialize().toHex())

        return sendTransaction(req.tx)
    }

    /**
     * (2) Use this when you want to join an /existing/ wallet.
     * You need to broadcast this transaction to all the old owners so they can sign it.
     * @param networkPublicHexKeys list of NEW wallet owners
     * @param entranceFee the entrance fee
     * @param oldTransactionSerialized the old transaction
     * @return the resulting transaction (unsigned multi-sig input!)
     */
    fun safeCreationJoinWalletTransaction(
        networkPublicHexKeys: List<String>,
        entranceFee: Coin,
        oldTransactionSerialized: String
    ): TransactionPackage {
        Log.i("Coin", "Coin: (safeCreationJoinWalletTransaction start).")

        Log.i("Coin", "Coin: making a transaction with you in it for everyone to sign.")

        val newTransaction = Transaction(params)
        val oldTransaction = CTransaction().deserialize(oldTransactionSerialized.hexToBytes())

        val oldMultiSignatureOutput = oldTransaction.vout.filter { it.scriptPubKey.size == 35 }[0].nValue

        val newKeys = networkPublicHexKeys.map { publicHexKey: String ->
            Log.i("Coin", "Coin: de-serializing key $publicHexKey.")
            ECKey.fromPublicOnly(publicHexKey.hexToBytes())
        }

        val (_, aggPubKey) = MuSig.generate_musig_key(newKeys)

        val pubKeyDataMusig = aggPubKey.getEncoded(true)

        val programMusig = byteArrayOf(pubKeyDataMusig[0] and 1.toByte()).plus(pubKeyDataMusig.drop(1)).toHex()
        val version = 1
        val addressMuSig = Address.program_to_witness(version, programMusig.hexToBytes())

        // Calculate the final amount of coins (old coins + entrance fee) that will be the new multi-sig.
        val newMultiSignatureOutputMoney = Coin.valueOf(oldMultiSignatureOutput).add(entranceFee)
        newTransaction.addOutput(newMultiSignatureOutputMoney, org.bitcoinj.core.Address.fromString(params, addressMuSig))

        val multisigInput = newTransaction.addInput(Transaction(params, oldTransactionSerialized.hexToBytes()).outputs.filter { it.scriptBytes.size == 35 }[0])
        multisigInput.disconnect()

        val req = SendRequest.forTx(newTransaction)
        printTransactionInformation(req.tx)
        req.changeAddress = protocolAddress()
        kit.wallet().completeTx(req)

        Log.i("YEET", "newtxid: " + req.tx.txId.toString())
        Log.i("YEET", "serialized new tx: " + req.tx.bitcoinSerialize().toHex())

        return TransactionPackage(
            newTransaction.bitcoinSerialize().toHex()
        )
    }

    /**
     * (2.1) You are (part) owner of a wallet a proposer wants to join. Sign the new wallet
     * and send it back to the proposer.
     * @param newTransaction the new transaction
     * @param oldTransaction the old transaction
     * @param key the key that will be signed with
     * @return the signature (you need to send back)
     */
    fun safeSigningJoinWalletTransaction(
        oldTransaction: CTransaction,
        newTransaction: CTransaction,
        publicKeys: List<ECKey>,
        nonces: List<ECKey>,
        key: ECKey
    ): BigInteger {
        Log.i("Coin", "Coin: (safeSigningJoinWalletTransaction start).")

        val (cMap, aggPubKey) = MuSig.generate_musig_key(publicKeys)

        val detKey = key as DeterministicKey

        val yeet = detKey.privKey
        Log.i("YEET", "privkey: $yeet")

        val privChallenge1 = detKey.privKey.multiply(BigInteger(1, cMap[key.decompress()])).mod(
            Schnorr.n
        )

        val index = oldTransaction.vout.indexOf(oldTransaction.vout.filter { it.scriptPubKey.size == 35 }[0])

        val sighashMuSig = CTransaction.TaprootSignatureHash(newTransaction, oldTransaction.vout, SIGHASH_ALL_TAPROOT, input_index = index.toShort())
        val signature = MuSig.sign_musig(
            ECKey.fromPrivate(privChallenge1), NONCE_KEY.first, MuSig.aggregate_schnorr_nonces(
                nonces
            ).first, aggPubKey, sighashMuSig
        )

        Log.i("YEET", "nonce_key priv: " + NONCE_KEY.first.privKey.toString())

        return signature
    }

    /**
     * (2.2) You are the proposer. You have collected the needed signatures and
     * will make the final transaction.
     * @param signaturesOfOldOwners signatures (of the OLD owners only, in correct order)
     * @param aggregateNonce
     * @param newTransaction SendRequest
     * @return TransactionPackage?
     */
    fun safeSendingJoinWalletTransaction(
        signaturesOfOldOwners: List<BigInteger>,
        aggregateNonce: ECPoint,
        newTransaction: CTransaction,
        context: Context
    ): Pair<Boolean, String> {
        Log.i("Coin", "Coin: (safeSendingJoinWalletTransaction start).")
        Log.i("Coin", "Coin: make the new final transaction for the new wallet.")
        Log.i("Coin", "Coin: using ${signaturesOfOldOwners.size} signatures.")

        val aggregateSignature = MuSig.aggregate_musig_signatures(signaturesOfOldOwners, aggregateNonce)

        val index = newTransaction.vin.indexOf(newTransaction.vin.filter { it.scriptSig.isEmpty() }[0])

        val cTxInWitness = CTxInWitness(arrayOf(aggregateSignature))
        val cTxWitness = CTxWitness(arrayOf(CTxInWitness(), CTxInWitness())) // TODO probably correct that there are only 2 inputs
        cTxWitness.vtxinwit[index] = cTxInWitness

        newTransaction.wit = cTxWitness

        val yeet = Transaction(params, newTransaction.serialize())
        print(yeet)

        val transaction = sendTransaction(Transaction(params, newTransaction.serialize())).broadcast().get(CoinCommunity.DEFAULT_BITCOIN_MAX_TIMEOUT, TimeUnit.SECONDS)
        println(context)
        return Pair(true, CoinCommunity.getSerializedTransaction(transaction))

//        return Pair(sendTaprootTransaction(newTransaction), newTransaction.serialize().toHex())
    }

    /**
     * (3.1) There is a set-up multi-sig wallet and a proposal, create a signature
     * for the proposal.
     * The transaction includes an output for residual funds using calculated fee estimates.
     * @param transaction PREVIOUS transaction with the multi-sig output
     * @param myPrivateKey key to sign with (yourself most likely)
     * @param receiverAddress receiver address
     * @param paymentAmount amount for receiver address
     * @return ECDSASignature
     */
    fun safeSigningTransactionFromMultiSig(
        oldTransactionSerialized: String,
        newTransaction: CTransaction,
        publicKeys: List<ECKey>,
        nonces: List<ECKey>,
        key: ECKey,
        receiverAddress: org.bitcoinj.core.Address,
        paymentAmount: Long
    ): BigInteger {
        Log.i("Coin", "Coin: (safeSigningTransactionFromMultiSig start).")

        val (cMap, aggPubKey) = MuSig.generate_musig_key(publicKeys)

        val detKey = key as DeterministicKey

        val oldTransaction = CTransaction().deserialize(oldTransactionSerialized.hexToBytes())
        val outpoint = COutPoint(Transaction(params, oldTransactionSerialized.hexToBytes()).txId.toString())

//        Populate the transaction inputs
        newTransaction.vin = arrayOf(CTxIn(outpoint))
        // TODO: Check if receiverAddress.hash is correct
        newTransaction.vout = arrayOf(CTxOut(paymentAmount, receiverAddress.hash))

        val privChallenge1 = detKey.privKey.multiply(BigInteger(1, cMap[key.decompress()])).mod(Schnorr.n)

        val sighashMuSig = CTransaction.TaprootSignatureHash(newTransaction, arrayOf(oldTransaction.vout[0]), SIGHASH_ALL_TAPROOT, input_index = 0)

        return MuSig.sign_musig(ECKey.fromPrivate(privChallenge1), NONCE_KEY.first, MuSig.aggregate_schnorr_nonces(nonces).first, aggPubKey, sighashMuSig)
    }

    /**
     * (3.2) There is a set-up multi-sig wallet and there are enough signatures
     * to broadcast a transaction with.
     * The transaction includes an output for residual funds using calculated fee estimates.
     * @param transaction transaction with multi-sig output.
     * @param signatures signatures of owners (yourself included)
     * @param receiverAddress receiver address
     * @param paymentAmount amount for receiver address
     * @return transaction
     */
    fun safeSendingTransactionFromMultiSig(
        signaturesOfOldOwners: List<BigInteger>,
        aggregateNonce: ECPoint,
        transactionSerialized: String,
        receiverAddress: org.bitcoinj.core.Address,
        paymentAmount: Long,
        context: Context
    ): Pair<Boolean, String> {
        Log.i("Coin", "Coin: (safeSendingTransactionFromMultiSig start).")
        val newTransaction = CTransaction()

//      Populate the transaction inputs
        val outpoint = COutPoint(Transaction(params, transactionSerialized.hexToBytes()).txId.toString())
        newTransaction.vin = arrayOf(CTxIn(outpoint))
        // TODO: Check if receiverAddress.hash is correct
        newTransaction.vout = arrayOf(CTxOut(paymentAmount, receiverAddress.hash))

        val aggregateSignature = MuSig.aggregate_musig_signatures(signaturesOfOldOwners, aggregateNonce)

        val cTxInWitness = CTxInWitness(arrayOf(aggregateSignature))
        val cTxWitness = CTxWitness(arrayOf(cTxInWitness))

        newTransaction.wit = cTxWitness

        return Pair(sendTaprootTransaction(newTransaction, context), newTransaction.serialize().toHex())
    }

    private fun sendTaprootTransaction(transaction: CTransaction, context: Context): Boolean {
        // TODO: submit serialized transaction string to python server
        Log.i("YEET", "transaction serialized: ${transaction.serialize().toHex()}")

        val yeet = transaction.serialize().toHex()
        print(yeet)

        println(context)

//        val context = // todo
//
//        val queue = Volley.newRequestQueue(context)
//        val url = "https://$REG_TEST_FAUCET_IP/generateBlock?tx_id=${transaction.serialize().toHex()}"
//
//        val stringRequest = StringRequest(
//            Request.Method.GET, url,
//            {
//                Toast.makeText(context, "YEEEEEEEEEEEEET", Toast.LENGTH_SHORT).show()
//                Thread.sleep(500)
//            },
//            { error ->
//                Toast.makeText(context, error.toString(), Toast.LENGTH_SHORT).show()
//            })
//
//        queue.add(stringRequest)

        return true
    }

    /**
     * Helper method to send transaction with logs and progress logs.
     * @param transaction transaction
     */
    private fun sendTransaction(transaction: Transaction): TransactionBroadcast {
        Log.i("Coin", "Coin: (sendTransaction start).")
        Log.i("Coin", "Coin: txId: ${transaction.txId}")
//        printTransactionInformation(transaction)

        val yeet = transaction.bitcoinSerialize().toHex()
        print(yeet)

        Log.i("Coin", "Waiting for $MIN_BLOCKCHAIN_PEERS peers")
        Log.i("Coin", "There are currently ${kit.peerGroup().connectedPeers.size} peers")
        kit.peerGroup().waitForPeers(MIN_BLOCKCHAIN_PEERS).get()
        Log.i("Coin", "Got >= $MIN_BLOCKCHAIN_PEERS peers: ${kit.peerGroup().connectedPeers}")
        Log.i(
            "Coin",
            "Transaction broadcast setup ${transaction.txId} completed. Not broadcasted yet."
        )
        return kit.peerGroup().broadcastTransaction(transaction)
    }

    /**
     * Helper method to get the multi-sig output from a transaction.
     * NOTE: make sure that there is an actual multi-sig output!
     * @param transaction transaction with multi-sig output.
     * @return the multi-sig output
     */
    fun getMuSigOutput(transaction: Transaction): MuSigOutputMeta {
        val multiSigOutputs = mutableListOf<TransactionOutput>()
        transaction.outputs.forEach { output ->
            multiSigOutputs.add(output)
        }

        // TODO check if program in multisig is same as program of musig agg pubkey
        val multiSigOutput = multiSigOutputs[0]

        return MuSigOutputMeta(
            multiSigOutput.value,
            multiSigOutput.index,
            multiSigOutput
        )
    }

    /**
     * Helper method to attempt to get the transaction from a transaction ID
     * and return serialized version.
     * @param transactionId transactionId
     * @return null if not available in your wallet yet, else serialized version of transaction.
     */
    fun attemptToGetTransactionAndSerialize(transactionId: String): String? {
        val transaction = kit.wallet().getTransaction(Sha256Hash.wrap(transactionId))
        if (transaction != null) {
            Log.i("Coin", "Transaction (attemptToGetTransactionAndSerialize) $transaction found")
            val serializedTransaction = transaction.bitcoinSerialize().toHex()
            return serializedTransaction
        } else {
            Log.i(
                "Coin", "Coin: (attemptToGetTransactionAndSerialize) " +
                "the transaction $transaction could not be found in your wallet."
            )
            return null
        }
    }

    /**
     * Helper method that prints useful information about a transaction.
     * @param transaction Transaction
     */
    private fun printTransactionInformation(transaction: Transaction) {
        Log.i("Coin", "Coin: ============ Transaction Information ===============")
        Log.i("Coin", "Coin: txId ${transaction.txId}")
        Log.i("Coin", "Coin: fee ${transaction.fee}")
        Log.i("Coin", "Coin: inputs:::")
        transaction.inputs.forEach {
            Log.i("Coin", "Coin:    index ${it.index}")
            Log.i("Coin", "Coin:    value ${it.value}")
            Log.i("Coin", "Coin:    multi-sig ${ScriptPattern.isSentToMultisig(it.scriptSig)}")
        }
        Log.i("Coin", "Coin: outputs:::")
        transaction.outputs.forEach {
            Log.i("Coin", "Coin:    index ${it.index}")
            Log.i("Coin", "Coin:    value ${it.value}")
            Log.i("Coin", "Coin:    multi-sig ${ScriptPattern.isSentToMultisig(it.scriptPubKey)}")
        }
        Log.i("Coin", "Coin: multi-sig output::")
        val a = getMuSigOutput(transaction)
        Log.i("Coin", "Coin:    value -> ${a.value}")
        Log.i("Coin", "Coin: ============ Transaction Information ===============")
    }

    /**
     * Creates a MultiSig payment transaction
     *
     * The two outputs of this transaction will be one to the receiver address (the payment) and
     *  one for the residual funds back to the MultiSig address.
     * Uses a previously created MultiSig output as the input of this transaction.
     * Optionally, the input MultiSig can be signed using the inputScriptSig field.
     *
     * @param receiverAddress: the receiver of the payment
     * @param paymentAmount: the amount to be transferred/payed to the receiver
     * @param previousMultiSigOutput: the MultiSig output of the shared wallet, used as new input
     * @param inputScriptSig: (Optional) the input script (ScriptBuilder.createMultiSigInputScript)
     *  created using signatures (TransactionSignature) of a transaction made with this method.
     */
    private fun createMultiSigPaymentTransaction(
        receiverAddress: org.bitcoinj.core.Address,
        paymentAmount: Coin,
        previousMultiSigOutput: TransactionOutput,
        inputScriptSig: Script? = null
    ): Transaction {
        // Get multisig script of previous output, which will become the input of this tx
        val multiSigScript = previousMultiSigOutput.scriptPubKey

        Log.i("Coin", "Coin: making the transaction (again) that will be sent.")
        val spendTx = Transaction(params)
        spendTx.addOutput(paymentAmount, receiverAddress)
        // Use a placeholder value for the residual output. Size of Tx needs to be accurate to estimate fee.
        val tempResidualOutput = spendTx.addOutput(Coin.valueOf(9999), multiSigScript)
        // Be careful with adding more inputs!! We assume the first input is the multisig input
        val input = spendTx.addInput(previousMultiSigOutput)

        // Calculate fee and set the change output corresponding to calculated fee
        val calculatedFeeValue = CoinUtil.calculateEstimatedTransactionFee(
            spendTx,
            params,
            CoinUtil.TxPriority.LOW_PRIORITY
        )
        // Make sure that the fee does not exceed the amount of funds available
        val calculatedFee =
            Coin.valueOf(calculatedFeeValue.coerceAtMost((previousMultiSigOutput.value - paymentAmount).value))
        val residualFunds = previousMultiSigOutput.value - paymentAmount - calculatedFee
        Log.i(
            "Coin",
            "Coin: Setting output for residual funds ${residualFunds.value} based on a calculated fee of $calculatedFee satoshi."
        )

        tempResidualOutput.value = residualFunds

        // Set input script signatures if passed to the method
        if (inputScriptSig != null) {
            input.scriptSig = inputScriptSig
        }

        return spendTx
    }

    /**
     * Wrapper for creating a MultiSig payment transaction without an input signature
     *
     * @param receiverAddress: the receiver of the payment
     * @param paymentAmount: the amount to be transferred/payed to the receiver
     * @param previousMultiSigOutput: the MultiSig output of the shared wallet, used as new input
     */
    fun createMultiSigPaymentTx(
        receiverAddress: org.bitcoinj.core.Address,
        paymentAmount: Coin,
        previousMultiSigOutput: TransactionOutput
    ): Transaction {
        return createMultiSigPaymentTransaction(
            receiverAddress,
            paymentAmount,
            previousMultiSigOutput
        )
    }

    /**
     * Wrapper for creating a MultiSig payment transaction with input signature
     *
     * @param receiverAddress: the receiver of the payment
     * @param paymentAmount: the amount to be transferred/payed to the receiver
     * @param previousMultiSigOutput: the MultiSig output of the shared wallet, used as new input
     * @param inputScriptSig: (Optional) the input script (ScriptBuilder.createMultiSigInputScript)
     *  created using signatures (TransactionSignature) of a transaction made with this method.
     */
    fun createMultiSigPaymentTxWithInputSig(
        receiverAddress: org.bitcoinj.core.Address,
        paymentAmount: Coin,
        previousMultiSigOutput: TransactionOutput,
        inputScriptSig: Script
    ): Transaction {
        return createMultiSigPaymentTransaction(
            receiverAddress,
            paymentAmount,
            previousMultiSigOutput,
            inputScriptSig
        )
    }

    companion object {

        fun createMultiSignatureWallet(
            publicKeys: List<ECKey>,
            entranceFee: Coin,
            threshold: Int,
            params: NetworkParameters = MainNetParams.get()
        ): Transaction {
            // Prepare a template for the contract.
            val contract = Transaction(params)

            // Prepare a list of all keys present in contract.
            val keys = Collections.unmodifiableList(publicKeys)

            // Create a n-n multi-signature output script.
            val script = ScriptBuilder.createMultiSigOutputScript(threshold, keys)

            // Now add an output with the entrance fee & script.
            contract.addOutput(entranceFee, script)

            return contract
        }

        fun checkEntranceFeeTransaction(
            userBitcoinPk: Address,
            bitcoinTransactionHash: Sha256Hash,
            sharedWalletBitcoinPk: Address,
            entranceFee: Double
        ): Boolean {
            // Get transaction from tx hash
            val blockExplorer = BlockExplorer()
            val tx = try {
                blockExplorer.getTransaction(bitcoinTransactionHash.toString())
            } catch (e: APIException) {
                // Return false if API Exception is thrown with reason "Transaction not found: (...)"
                // The library does not support accessing the reason, so use JSON parser to parse message to a JSON object
                if (e.message !== null) {
                    val reason = JsonParser().parse(e.message).asJsonObject.get("reason")
                    if (reason != null) {
                        if (reason.asString.startsWith("Transaction not found: ")) {
                            return false
                        }
                    }
                }

                // API Exception was something other than transaction not found, so still throw it
                throw e
            }

            // Check block confirmations
            val blockHeightRelative = blockExplorer.latestBlock.height - tx.blockHeight
            if (blockHeightRelative < 6) {
                println("Transaction was not confirmed by at least 6 blocks:  $blockHeightRelative")
                return false
            }
            if (tx.blockHeight < 0) {
                println("Transaction does not have a valid block height: ${tx.blockHeight}")
                return false
            }

            // Check transaction inputs
            val userBitcoinPkString = userBitcoinPk.toString()
            var hasCorrectInput = false
            for (input in tx.inputs) {
                val inputValue = input.previousOutput.value.toDouble() / 100000000
                if (userBitcoinPkString.equals(input.previousOutput.address) &&
                    inputValue >= entranceFee
                ) {
                    hasCorrectInput = true
                    break
                }
            }

            if (!hasCorrectInput) {
                println("Transaction did not have correct inputs")
                return false
            }

            // Check transaction outputs
            val sharedWalletBitcoinPkString = sharedWalletBitcoinPk.toString()
            var hasCorrectOutput = false
            for (output in tx.outputs) {
                val outputValue = output.value.toDouble() / 100000000
                if (sharedWalletBitcoinPkString.equals(output.address) &&
                    outputValue >= entranceFee
                ) {
                    hasCorrectOutput = true
                    break
                }
            }

            if (!hasCorrectOutput) {
                println("Transaction did not have correct outputs")
                return false
            }

            return true
        }

        fun privateKeyStringToECKey(
            privateKey: String,
            params: NetworkParameters = MainNetParams.get()
        ): ECKey {
            return DumpedPrivateKey.fromBase58(params, privateKey).key
        }

        fun ecKeyToPrivateKeyString(
            ecKey: ECKey,
            params: NetworkParameters = MainNetParams.get()
        ): String {
            return ecKey.getPrivateKeyAsWiF(params)
        }

        /**
         * A method to create a serialized seed for use in BitcoinJ.
         * @param paramsRaw BitcoinNetworkOptions
         * @return SerializedDeterministicKey
         */
        fun generateRandomDeterministicSeed(paramsRaw: BitcoinNetworkOptions): SerializedDeterministicKey {
            val params = when (paramsRaw) {
                BitcoinNetworkOptions.TEST_NET -> TestNet3Params.get()
                BitcoinNetworkOptions.PRODUCTION -> MainNetParams.get()
                BitcoinNetworkOptions.REG_TEST -> RegTestParams.get()
            }
            val keyChainGroup = KeyChainGroup.builder(params, KeyChainGroupStructure.DEFAULT)
                .fromRandom(Script.ScriptType.P2PKH).build()
            return deterministicSeedToSerializedDeterministicKey(keyChainGroup.activeKeyChain.seed!!)
        }

        fun deterministicSeedToSerializedDeterministicKey(seed: DeterministicSeed): SerializedDeterministicKey {
            val words = Joiner.on(" ").join(seed.mnemonicCode)
            val creationTime = seed.creationTimeSeconds
            return SerializedDeterministicKey(words, creationTime)
        }
    }

    fun toSeed(): SerializedDeterministicKey {
        val seed = kit.wallet().keyChainSeed
        return deterministicSeedToSerializedDeterministicKey(seed)
    }

    fun addKey(privateKey: String) {
        Log.i("Coin", "Coin: Importing key in existing wallet: $privateKey")
        this.kit.wallet().importKey(formatKey(privateKey))
    }

    data class TransactionPackage(
        val serializedTransaction: String
    )

    data class MuSigOutputMeta(
        val value: Coin,
        val index: Int,
        val unsignedOutput: TransactionOutput
    )
}
