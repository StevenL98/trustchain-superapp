package nl.tudelft.trustchain.currencyii.util.taproot

import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import org.junit.Assert
import org.junit.Test

class CTransactionTest {

    @Test
    fun taprootSignature() {
        val hash = "9810b6e1b42d1f5d3c9377c5b1c3b6bb2ee0f96427d9adead6c99626798faac8"
        val publicKey: ByteArray = "001420501761e7ba8b479cc516488d47e8f5d02e52d7".hexToBytes()
        val coutPoint = COutPoint(hash = hash, n = 0)
        val cTxIn = CTxIn(prevout = coutPoint, scriptSig = byteArrayOf(), nSequence = 0)
        val cTxOut = CTxOut(nValue = (0.50000000 * 100_000_000).toLong(), scriptPubKey = publicKey)
        val spending_tx = CTransaction(
            nVersion = 1,
            vin = arrayOf(cTxIn),
            vout = arrayOf(cTxOut),
            wit = CTxWitness(),
            nLockTime = 0
        )

        val publicKey2 =
            "5121003dd5fc3c1766d0a73466a5997da83efcc529107c9ecd0c56e2a28519f0eb3104".hexToBytes()
        val txVout = CTxOut(nValue = (1.00000000 * 100_000_000).toLong(), scriptPubKey = publicKey2)

        val expected =
            "c58660789cf1bbd4c265823168ccdc2e13a5f97c4d2e8742e08e16ee21d0929a"
        val actual =
            CTransaction.TaprootSignatureHash(
                spending_tx,
                arrayOf(txVout),
                SIGHASH_ALL_TAPROOT,
                input_index = 0
            ).toHex()

        Assert.assertEquals(expected, actual)
    }

    @Test
    fun cTxInSerialize() {
        val prevout = COutPoint(
            hash = "92ba46893466d7d219a3980db4bf33206cf85ec5ce7726e9062b943d62fe995d",
            n = 0
        )
        val ctxIn = CTxIn(prevout = prevout)
        val expected =
            "5d99fe623d942b06e92677cec55ef86c2033bfb40d98a319d2d766348946ba92000000000000000000"
        val actual = ctxIn.serialize().toHex()

        Assert.assertEquals(expected, actual)
    }

    @Test
    fun cTransactionSerializeWithWitness2() {
        val hash = "9810b6e1b42d1f5d3c9377c5b1c3b6bb2ee0f96427d9adead6c99626798faac8"
        val publicKey: ByteArray = "001420501761e7ba8b479cc516488d47e8f5d02e52d7".hexToBytes()
        val coutPoint = COutPoint(hash = hash, n = 0)
        val cTxIn = CTxIn(prevout = coutPoint, scriptSig = byteArrayOf(), nSequence = 0)
        val cTxOut = CTxOut(nValue = (0.50000000 * 100_000_000).toLong(), scriptPubKey = publicKey)
        val spending_tx = CTransaction(
            nVersion = 1,
            vin = arrayOf(cTxIn),
            vout = arrayOf(cTxOut),
            wit = CTxWitness(),
            nLockTime = 0
        )

        spending_tx.wit.vtxinwit = arrayOf(CTxInWitness(arrayOf("7bdd007a2ada0fbf18fe8ea7858398e2775195db1a2cef127ef38eef861027bf4f058be84a536603799b4acce1f0eeb048c634d740aa38351cb18b7465e4b125".hexToBytes())))
        val expected = "01000000000101c8aa8f792696c9d6eaadd92764f9e02ebbb6c3b1c577933c5d1f2db4e1b610980000000000000000000180f0fa020000000016001420501761e7ba8b479cc516488d47e8f5d02e52d701407bdd007a2ada0fbf18fe8ea7858398e2775195db1a2cef127ef38eef861027bf4f058be84a536603799b4acce1f0eeb048c634d740aa38351cb18b7465e4b12500000000"
        val actual = spending_tx.serialize().toHex()

        Assert.assertEquals(expected, actual)
    }

    @Test
    fun cTransactionDeserializeWithWitness() {
        val serialized = "01000000000101c8aa8f792696c9d6eaadd92764f9e02ebbb6c3b1c577933c5d1f2db4e1b610980000000000000000000180f0fa020000000016001420501761e7ba8b479cc516488d47e8f5d02e52d701407bdd007a2ada0fbf18fe8ea7858398e2775195db1a2cef127ef38eef861027bf4f058be84a536603799b4acce1f0eeb048c634d740aa38351cb18b7465e4b12500000000"

        val hash = "9810b6e1b42d1f5d3c9377c5b1c3b6bb2ee0f96427d9adead6c99626798faac8"
        val publicKey: ByteArray = "001420501761e7ba8b479cc516488d47e8f5d02e52d7".hexToBytes()
        val coutPoint = COutPoint(hash = hash, n = 0)
        val cTxIn = CTxIn(prevout = coutPoint, scriptSig = byteArrayOf(), nSequence = 0)
        val cTxOut = CTxOut(nValue = (0.50000000 * 100_000_000).toLong(), scriptPubKey = publicKey)
        val spending_tx = CTransaction(
            nVersion = 1,
            vin = arrayOf(cTxIn),
            vout = arrayOf(cTxOut),
            wit = CTxWitness(),
            nLockTime = 0
        )

        spending_tx.wit.vtxinwit = arrayOf(CTxInWitness(arrayOf("7bdd007a2ada0fbf18fe8ea7858398e2775195db1a2cef127ef38eef861027bf4f058be84a536603799b4acce1f0eeb048c634d740aa38351cb18b7465e4b125".hexToBytes())))

        val expected = serialized
        val actual = CTransaction().deserialize(serialized.hexToBytes()).serialize()

        Assert.assertEquals(expected, actual)
    }

    @Test
    fun cTransactionSerializeWithWitness() {
        val hash = "92ba46893466d7d219a3980db4bf33206cf85ec5ce7726e9062b943d62fe995d"
        val publicKey: ByteArray = "0014062b4b39acb17d358f130b15ca3e92acfe1604d6".hexToBytes()
        val cOutpoint = COutPoint(hash = hash, n = 0)
        val ctxIn = CTxIn(prevout = cOutpoint)
        val cTxOut = CTxOut(nValue = (0.69000000 * 100_000_000).toLong(), scriptPubKey = publicKey)
        val cTxInWitness =
            CTxInWitness(arrayOf("475ecd0cfdbbadb8fa891e7c341e6290a0ceb13f16d9943d57600bc93c26b7b5efdd37da69d86fe73d38331d407521e08c2dcde0400cdb8cb5938e7e7d831536".hexToBytes()))
        val cTxWitness = CTxWitness(arrayOf(cTxInWitness))
        val cTransaction = CTransaction(
            nVersion = 1,
            vin = arrayOf(ctxIn),
            vout = arrayOf(cTxOut),
            wit = cTxWitness,
            nLockTime = 0
        )

        val expected = "010000000001015d99fe623d942b06e92677cec55ef86c2033bfb40d98a319d2d766348946ba920000000000000000000140db1c0400000000160014062b4b39acb17d358f130b15ca3e92acfe1604d60140475ecd0cfdbbadb8fa891e7c341e6290a0ceb13f16d9943d57600bc93c26b7b5efdd37da69d86fe73d38331d407521e08c2dcde0400cdb8cb5938e7e7d83153600000000"
        val actual = cTransaction.serialize().toHex()

        Assert.assertEquals(expected, actual)
    }
}
