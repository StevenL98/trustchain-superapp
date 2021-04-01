package nl.tudelft.trustchain.currencyii.util.taproot

import nl.tudelft.ipv8.util.sha256
import nl.tudelft.ipv8.util.toHex
import java.nio.ByteBuffer
import kotlin.experimental.and

class CTransaction(
    val nVersion: Int = 1,
    val vin: Array<CTxIn> = arrayOf(),
    val vout: Array<CTxOut> = arrayOf(),
    val wit: CTxWitness = CTxWitness(),
    val nLockTime: Int = 0,
    val sha256: UInt? = null,
    val hash: UInt? = null
)

class CTxIn(
    val prevout: COutPoint = COutPoint(),
    val scriptSig: ByteArray = byteArrayOf(),
    val nSequence: Int = 0
)

class CTxOut(
    val nValue: Long = 0,
    val scriptPubKey: ByteArray = byteArrayOf()
) {
    fun serialize(): ByteArray {
        var r: ByteArray = byteArrayOf()
        r += ByteBuffer.allocate(1).putLong(nValue).array()
        r += Messages.ser_string(scriptPubKey)
        return r
    }
}

class CTxWitness(val vtxinwit: Array<CTxIn> = arrayOf())

class COutPoint(
    var hash: Byte = 0,
    var n: Int = 0
) {

    fun serialize(): ByteArray {
        var r: ByteArray = byteArrayOf()
        r += ser_uint256(hash.toInt())
        val r_buf: ByteBuffer = ByteBuffer.wrap(r)
        r_buf.putInt(n)
        r += r_buf.array()
        return r
    }
}

class CScript(val bytes: ByteArray = byteArrayOf()) {
    fun size(): Int {
        return bytes.size
    }

    fun toHex(): String {
        return bytes.toHex()
    }
}

class CScriptOp(private val n: Int) {
    override fun equals(other: Any?): Boolean {
        return n.toByte() == other
    }

    override fun hashCode(): Int {
        return n
    }
}

val DEFAULT_TAPSCRIPT_VER = 0xc0.toByte()
val TAPROOT_VER = 0
val SIGHASH_ALL_TAPROOT: Byte = 0
val SIGHASH_ALL: Byte = 1
val SIGHASH_NONE: Byte = 2
val SIGHASH_SINGLE: Byte = 3
val SIGHASH_ANYONECANPAY: Byte = 0x80.toByte()

val OP_HASH160 = CScriptOp(0xa9)
val OP_EQUAL = CScriptOp(0x87)
val OP_1 = CScriptOp(0x51)
val ANNEX_TAG = 0x50.toByte()
fun ser_uint256(u_in: Int): ByteArray {
    var u = u_in
    var rs: ByteArray = byteArrayOf()
    for (i in 0..8) {
        rs += ByteBuffer.allocate(1).putInt((u and 0xFFFFFFFF.toInt()).toInt()).array()
        u = u shr 32
    }
    return rs
}

fun isPayToScriptHash(script: ByteArray): Boolean {
    return script.size == 23 && OP_HASH160.equals(script[0]) && script[1].toInt() == 20 && OP_EQUAL.equals(
        script[22]
    )
}

fun isPayToTaproot(script: ByteArray): Boolean {
    return script.size == 35 && OP_1.equals(script[0]) && script[1].toInt() == 33 && script[2] >= 0 && script[2] <= 1
}

fun tagged_hash(tag: String, data: ByteArray): ByteArray {
    var ss = sha256(tag.toByteArray(Charsets.UTF_8))
    ss += ss
    ss += data
    return sha256(ss)
}

fun TaprootSignatureHash(
    txTo: CTransaction,
    spent_utxos: Array<CTxOut>,
    hash_type: Byte,
    input_index: Short = 0,
    scriptpath: Boolean = false,
    tapscript: CScript = CScript(),
    codeseparator_pos: Int = -1,
    annex: ByteArray? = null,
    tapscript_ver: Byte = DEFAULT_TAPSCRIPT_VER
): ByteArray {
    assert(txTo.vin.size == spent_utxos.size)
    assert((hash_type in 0..3) || (hash_type in 0x81..0x83))
    assert(input_index < txTo.vin.size)

    val spk = spent_utxos[input_index.toInt()].scriptPubKey
    val ss_buf: ByteBuffer = ByteBuffer.wrap(byteArrayOf(0, hash_type)) // Epoch, hash_type
    ss_buf.putInt(txTo.nVersion)
    ss_buf.putInt(txTo.nLockTime)
    var ss: ByteArray = ss_buf.array()

    if (hash_type and SIGHASH_ANYONECANPAY != 0.toByte()) {
        ss += sha256(txTo.vin.map { it.prevout.serialize() })
        var temp: ByteBuffer = ByteBuffer.allocate(spent_utxos.size)
        for (u in spent_utxos) {
            temp.putLong(u.nValue)
        }
        ss += sha256(temp.array())
        temp = ByteBuffer.allocate(txTo.vin.size)
        for (i in txTo.vin) {
            temp.putInt(i.nSequence)
        }
        ss += sha256(temp.array())
    }
    if ((hash_type and 3) != SIGHASH_SINGLE && (hash_type and 3) != SIGHASH_NONE) {
        ss += sha256(txTo.vout.map { it.serialize() })
    }
    var spend_type = 0
    if (isPayToScriptHash(spk)) {
        spend_type = 1
    } else {
        assert(isPayToTaproot(spk))
    }
    if (annex != null) {
        assert(annex[0] == ANNEX_TAG)
        spend_type = spend_type or 2
    }
    if (scriptpath) {
        assert(tapscript.size() > 0)
        assert(codeseparator_pos >= -1)
        spend_type = spend_type or 4
    }
    ss += byteArrayOf(spend_type.toByte())
    ss += Messages.ser_string(spk)
    if (hash_type and SIGHASH_ANYONECANPAY != 0.toByte()) {
        ss += txTo.vin[input_index.toInt()].prevout.serialize()
        ss += ByteBuffer.allocate(1).putLong(spent_utxos[input_index.toInt()].nValue).array()
        ss += ByteBuffer.allocate(1).putInt(txTo.vin[input_index.toInt()].nSequence).array()
    } else {
        ss += ByteBuffer.allocate(1).putShort(input_index).array()
    }
    if ((spend_type and 2) != 0) {
        ss += sha256(Messages.ser_string(annex!!))
    }
    if ((hash_type and 3) == SIGHASH_SINGLE) {
        assert(input_index < txTo.vout.size)
        ss += sha256(txTo.vout[input_index.toInt()].serialize())
    }
    if (scriptpath) {
        ss += tagged_hash("TapLeaf", byteArrayOf(tapscript_ver) + Messages.ser_string(tapscript.bytes))
        ss += byteArrayOf(0x02)
        ss += ByteBuffer.allocate(1).putInt(codeseparator_pos).array()
    }
    assert(
        ss.size == 177 - (hash_type and SIGHASH_ANYONECANPAY) * 50 - (hash_type and 3 == SIGHASH_NONE).compareTo(
            true
        ) * 32 - (isPayToScriptHash(spk)).compareTo(true) * 12 + (annex != null).compareTo(true) * 32 + scriptpath.compareTo(
            true
        ) * 35
    )
    return tagged_hash("TapSighash", ss)
}
