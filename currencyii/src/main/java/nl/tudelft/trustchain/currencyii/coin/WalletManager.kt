package nl.tudelft.trustchain.currencyii.coin

import android.content.Context
import android.util.Log
import com.google.common.base.Joiner
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
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
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.KeyChainGroup
import org.bitcoinj.wallet.KeyChainGroupStructure
import org.bitcoinj.wallet.SendRequest
import org.bouncycastle.math.ec.ECPoint
import java.io.File
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.*
import kotlin.experimental.and

const val TEST_NET_WALLET_NAME = "forwarding-service-testnet"
const val REG_TEST_WALLET_NAME = "forwarding-service-regtest"
const val MAIN_NET_WALLET_NAME = "forwarding-service"
const val MIN_BLOCKCHAIN_PEERS_TEST_NET = 5
const val MIN_BLOCKCHAIN_PEERS_REG_TEST = 1
const val MIN_BLOCKCHAIN_PEERS_PRODUCTION = 5
const val REG_TEST_FAUCET_IP = "131.180.27.224"
const val REG_TEST_FAUCET_DOMAIN = "taproot.tribler.org"

var MIN_BLOCKCHAIN_PEERS = MIN_BLOCKCHAIN_PEERS_TEST_NET

// TODO refactor javadoc (make sure it's up to date) and add useful logging (also for other classes)

// TODO disable MAINNET and TESTNET buttons and add disclaimer to not send or use real bitcoins

// TODO Nonce per dao and refresh (this is fixed after steven merges his branch)

// TODO after signing, go back to previous screen

// TODO tests

// TODO test different emulators joining mutual dao and sending funds

// TODO either remove or keep fees

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

    /**
     * Return the public point of the nonce key
     * @param swUniqueId - String, the id of the DAO where you want to get the nonce from
     */
    fun nonceECPointHex(swUniqueId: String, context: Context): String {
        return getNonceKey(swUniqueId, context).second.getEncoded(true).toHex()
    }

    /**
     * Return the public point of the nonce key
     * @param - Pair, the private and public key of the nonce
     */
    fun nonceECPointHex(nonce: Pair<ECKey, ECPoint>): String {
        return nonce.second.getEncoded(true).toHex()
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
    ): Pair<Boolean, String> {
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

        transaction.addOutput(
            entranceFee, org.bitcoinj.core.Address.fromString(params, addressMuSig)
        )

        val req = SendRequest.forTx(transaction)
        req.changeAddress = protocolAddress()
        kit.wallet().completeTx(req)

        Log.i("YEET", "txid: " + req.tx.txId.toString())
        Log.i("YEET", "serialized tx: " + req.tx.bitcoinSerialize().toHex())

        val serializedTransaction = req.tx.bitcoinSerialize()

        return Pair(
            sendTaprootTransaction(CTransaction().deserialize(serializedTransaction)),
            serializedTransaction.toHex()
        )
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
    ): String {
        val newTransaction = Transaction(params)
        val oldTransaction = CTransaction().deserialize(oldTransactionSerialized.hexToBytes())

        val oldMultiSignatureOutput =
            oldTransaction.vout.filter { it.scriptPubKey.size == 35 }[0].nValue

        val newKeys = networkPublicHexKeys.map { publicHexKey: String ->
            ECKey.fromPublicOnly(publicHexKey.hexToBytes())
        }

        val (_, aggPubKey) = MuSig.generate_musig_key(newKeys)

        val pubKeyDataMusig = aggPubKey.getEncoded(true)

        val programMusig =
            byteArrayOf(pubKeyDataMusig[0] and 1.toByte()).plus(pubKeyDataMusig.drop(1)).toHex()
        val version = 1
        val addressMuSig = Address.program_to_witness(version, programMusig.hexToBytes())

        val newMultiSignatureOutputMoney = Coin.valueOf(oldMultiSignatureOutput).add(entranceFee)
        newTransaction.addOutput(
            newMultiSignatureOutputMoney,
            org.bitcoinj.core.Address.fromString(params, addressMuSig)
        )

        val multisigInput = newTransaction.addInput(
            Transaction(
                params,
                oldTransactionSerialized.hexToBytes()
            ).outputs.filter { it.scriptBytes.size == 35 }[0]
        )
        multisigInput.disconnect()

        val req = SendRequest.forTx(newTransaction)
        req.changeAddress = protocolAddress()
        kit.wallet().completeTx(req)

        Log.i("YEET", "newtxid: " + req.tx.txId.toString())
        Log.i("YEET", "serialized new tx: " + req.tx.bitcoinSerialize().toHex())

        return newTransaction.bitcoinSerialize().toHex()
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
        key: ECKey,
        walletId: String,
        context: Context
    ): BigInteger {
        val (cMap, aggPubKey) = MuSig.generate_musig_key(publicKeys)

        val detKey = key as DeterministicKey

        val yeet = detKey.privKey
        Log.i("YEET", "privkey: $yeet")

        val privChallenge1 = detKey.privKey.multiply(BigInteger(1, cMap[key.decompress()])).mod(
            Schnorr.n
        )

        val index =
            oldTransaction.vout.indexOf(oldTransaction.vout.filter { it.scriptPubKey.size == 35 }[0])

        val sighashMuSig = CTransaction.TaprootSignatureHash(
            newTransaction,
            oldTransaction.vout,
            SIGHASH_ALL_TAPROOT,
            input_index = index.toShort()
        )
        val signature = MuSig.sign_musig(
            ECKey.fromPrivate(privChallenge1),
            getNonceKey(walletId, context).first,
            MuSig.aggregate_schnorr_nonces(
                nonces
            ).first,
            aggPubKey,
            sighashMuSig
        )

        Log.i("YEET", "nonce_key priv: " + getNonceKey(walletId, context).first.privKey.toString())

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
        newTransaction: CTransaction
    ): Pair<Boolean, String> {
        Log.i("Coin", "Coin: (safeSendingJoinWalletTransaction start).")
        Log.i("Coin", "Coin: make the new final transaction for the new wallet.")
        Log.i("Coin", "Coin: using ${signaturesOfOldOwners.size} signatures.")

        val aggregateSignature =
            MuSig.aggregate_musig_signatures(signaturesOfOldOwners, aggregateNonce)

        val index =
            newTransaction.vin.indexOf(newTransaction.vin.filter { it.scriptSig.isEmpty() }[0])

        val cTxInWitness = CTxInWitness(arrayOf(aggregateSignature))
        val cTxWitness = CTxWitness(
            arrayOf(
                CTxInWitness(),
                CTxInWitness()
            )
        ) // our transactions only have 2 inputs
        cTxWitness.vtxinwit[index] = cTxInWitness

        newTransaction.wit = cTxWitness

        return Pair(sendTaprootTransaction(newTransaction), newTransaction.serialize().toHex())
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
        publicKeys: List<ECKey>,
        nonces: List<ECKey>,
        key: ECKey,
        receiverAddress: org.bitcoinj.core.Address,
        paymentAmount: Long,
        walletId: String,
        context: Context
    ): BigInteger {
        val detKey = key as DeterministicKey

        val (cMap, aggPubKey) = MuSig.generate_musig_key(publicKeys)
        val pubKeyDataMuSig = aggPubKey.getEncoded(true)

        val (oldTransaction, newTransaction) = constructNewTransaction(
            oldTransactionSerialized,
            pubKeyDataMuSig,
            paymentAmount,
            receiverAddress
        )

        val privChallenge =
            detKey.privKey.multiply(BigInteger(1, cMap[key.decompress()])).mod(Schnorr.n)

        val sighashMuSig = CTransaction.TaprootSignatureHash(
            newTransaction,
            arrayOf(oldTransaction.vout.filter { it.scriptPubKey.size == 35 }[0]),
            SIGHASH_ALL_TAPROOT,
            input_index = 0
        )
        val signature = MuSig.sign_musig(
            ECKey.fromPrivate(privChallenge),
            getNonceKey(walletId, context).first,
            MuSig.aggregate_schnorr_nonces(
                nonces
            ).first,
            aggPubKey,
            sighashMuSig
        )

        return signature
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
        publicKeys: List<ECKey>,
        signaturesOfOldOwners: List<BigInteger>,
        aggregateNonce: ECPoint,
        oldTransactionSerialized: String,
        receiverAddress: org.bitcoinj.core.Address,
        paymentAmount: Long
    ): Pair<Boolean, String> {
        val (_, aggPubKey) = MuSig.generate_musig_key(publicKeys)
        val pubKeyDataMuSig = aggPubKey.getEncoded(true)

        val (_, newTransaction) = constructNewTransaction(
            oldTransactionSerialized,
            pubKeyDataMuSig,
            paymentAmount,
            receiverAddress
        )

        val aggregateSignature =
            MuSig.aggregate_musig_signatures(signaturesOfOldOwners, aggregateNonce)

        val cTxInWitness = CTxInWitness(arrayOf(aggregateSignature))
        val cTxWitness = CTxWitness(arrayOf(cTxInWitness))

        newTransaction.wit = cTxWitness

        return Pair(sendTaprootTransaction(newTransaction), newTransaction.serialize().toHex())
    }

    private fun constructNewTransaction(
        oldTransactionSerialized: String,
        pubKeyDataMuSig: ByteArray,
        paymentAmount: Long,
        receiverAddress: org.bitcoinj.core.Address
    ): Pair<CTransaction, CTransaction> {
        val newTransaction = Transaction(params)
        val oldTransaction = CTransaction().deserialize(oldTransactionSerialized.hexToBytes())

        val programMusig =
            byteArrayOf(pubKeyDataMuSig[0] and 1.toByte()).plus(pubKeyDataMuSig.drop(1)).toHex()
        val version = 1
        val addressMuSig = Address.program_to_witness(version, programMusig.hexToBytes())

        val newMultiSignatureOutputMoney = Coin.valueOf(paymentAmount)
        newTransaction.addOutput(newMultiSignatureOutputMoney, receiverAddress)

        val multisigInput = newTransaction.addInput(
            Transaction(
                params,
                oldTransactionSerialized.hexToBytes()
            ).outputs.filter { it.scriptBytes.size == 35 }[0]
        )
        multisigInput.disconnect()

        val oldMultiSignatureOutput =
            oldTransaction.vout.filter { it.scriptPubKey.size == 35 }[0].nValue

        newTransaction.addOutput(
            Coin.valueOf(oldMultiSignatureOutput - paymentAmount),
            org.bitcoinj.core.Address.fromString(params, addressMuSig)
        )

        val newCTx = CTransaction().deserialize(newTransaction.bitcoinSerialize())
        return Pair(oldTransaction, newCTx)
    }

    private fun sendTaprootTransaction(transaction: CTransaction): Boolean {
        Log.i("YEET", "transaction serialized: ${transaction.serialize().toHex()}")
        val url = URL(
            "https://$REG_TEST_FAUCET_DOMAIN/generateBlock?tx_id=${
                transaction.serialize().toHex()
            }"
        )

        val executor: ExecutorService =
            Executors.newCachedThreadPool(Executors.defaultThreadFactory())

        val future: Future<Boolean> = executor.submit(object : Callable<Boolean> {
            override fun call(): Boolean {
                val connection = url.openConnection() as HttpURLConnection

                try {
                    return connection.responseCode == 200
                } finally {
                    connection.disconnect()
                }
            }
        })

        return try {
            future.get(10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Store a private key that has already been calculated before
     * @param swUniqueId - String, the unique id of the DAO you want to create a nonce for
     * @param privateKey - String, the private key that needs to be stored
     */
    fun storeNonceKey(swUniqueId: String, context: Context, privateKey: String) {
        val nonceKeyData = context.getSharedPreferences("nonce_keys", 0)!!
        val editor = nonceKeyData.edit()
        Log.i("NONCE_KEY", "Stored key for DAO: $swUniqueId")
        Log.i("NONCE_KEY", privateKey)
        editor.putString(swUniqueId, privateKey)
        editor.apply()
    }

    /**
     * Create a new nonce per DAO and store them in a file in the shared preference folder
     * @param swUniqueId - String, the unique id of the DAO you want to create a nonce for
     * @return nonce key pair
     */
    fun addNewNonceKey(swUniqueId: String, context: Context): Pair<ECKey, ECPoint> {
        val nonceKeyData = context.getSharedPreferences("nonce_keys", 0)!!
        val nonce = Key.generate_schnorr_nonce(ECKey().privKeyBytes)
        val privateKey = nonce.first.privKey.toByteArray().toHex()
        val editor = nonceKeyData.edit()
        Log.i("NONCE_KEY", "New key created for DAO: $swUniqueId")
        Log.i("NONCE_KEY", privateKey)
        editor.putString(swUniqueId, privateKey)
        editor.apply()
        return nonce
    }

    /**
     * Get the nonce key that is stored for that specific DAO
     * @param swUniqueId - String, the unique id of the DAO you want to get a nonce for
     * @return nonce key pair
     */
    fun getNonceKey(swUniqueId: String, context: Context): Pair<ECKey, ECPoint> {
        val nonceKeyData = context.getSharedPreferences("nonce_keys", 0)!!
        return Key.generate_schnorr_nonce(nonceKeyData.getString(swUniqueId, "")!!.hexToBytes())
    }

    companion object {
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
}
