import java.math.BigInteger
import java.security.SecureRandom
import java.security.interfaces.ECKey
import kotlin.random.Random

class Musig(musigC: Map<BigInteger,BigInteger>, aggregateKey:BigInteger)

fun generate_musig_key(pubKeyList: List<BigInteger>): Musig {
    val srt = pubKeyList.sortedBy { it.toByte() }
    var L = ""
    for (px in srt) {
        L += px.toByteArray().drop(1)
    }
    var Lh = hash.EcSha256.hash(L.toByteArray())
    var musigC: MutableMap<BigInteger,BigInteger> = HashMap()
    var aggregateKey = BigInteger.ZERO
    for (key in pubKeyList) {
        musigC[key] = BigInteger(hash.EcSha256.hash(Lh +key.toByteArray().drop(1)))
        aggregateKey += key.multiply(musigC[key])
    }
    return Musig(musigC, aggregateKey)
}

fun generateSchnorrNonce() : BigInteger {
    val kp = BigInteger(Secp256k1.p.bitLength(), SecureRandom())
    val R = Secp256k1.affine(Secp256k1.multiply(Secp256k1.g, kp))
    if (jacobi_symbol(R.curve.p, Secp256k1.p) != 1) {
        return(kp)
    }
    else {
        return(Secp256k1.n - kp)
    }
}

class AggregatedSchorrNonce(point: EcPoint, negated: Boolean)

fun aggregateSchnorrNonces(noncePointList: List<EcPoint>) : AggregatedSchorrNonce {
    var R_agg:EcPoint = sum(noncePointList)
    val R_agg_affine = Secp256k1.affine(R_agg)
    var negated: Boolean = false
    if (jacobi_symbol(R_agg_affine.curve.p, Secp256k1.p) != 1) {
        negated = true
        val R_agg_negated =  Secp256k1.multiply(R_agg, Secp256k1.p - BigInteger.ONE)
        R_agg = R_agg_negated
        return AggregatedSchorrNonce(R_agg, negated)
    }
}

fun sum(list: List<EcPoint>) : EcPoint {
    var agg = EcPoint(BigInteger.ZERO,BigInteger.ZERO, BigInteger.ZERO,Secp256k1)
    for (i in list) {
        agg += i
    }
    return agg
}


//def sign_musig(priv_key, k_key, R_musig, P_musig, msg):
//"""Construct a MuSig partial signature and return the s value."""
//assert priv_key.valid
//assert priv_key.compressed
//assert P_musig.compressed
//assert len(msg) == 32
//assert k_key is not None and k_key.secret != 0
//assert jacobi_symbol(R_musig.get_y(), SECP256K1_FIELD_SIZE) == 1
//e = musig_digest(R_musig, P_musig, msg)
//return (k_key.secret + e * priv_key.secret) % SECP256K1_ORDER



//
//def sign_musig(priv_key, k_key, R_musig, P_musig, msg):
//"""Construct a MuSig partial signature and return the s value."""
//assert priv_key.valid
//assert priv_key.compressed
//assert P_musig.compressed
//assert len(msg) == 32
//assert k_key is not None and k_key.secret != 0
//assert jacobi_symbol(R_musig.get_y(), SECP256K1_FIELD_SIZE) == 1
//e = musig_digest(R_musig, P_musig, msg)
//return (k_key.secret + e * priv_key.secret) % SECP256K1_ORDER
//
//def musig_digest(R_musig, P_musig, msg):
//"""Get the digest to sign for musig"""
//return int.from_bytes(hashlib.sha256(R_musig.get_x().to_bytes(32, 'big') + P_musig.get_bytes() + msg).digest(), 'big') % SECP256K1_ORDER
//
//def aggregate_musig_signatures(s_list, R_musig):
//"""Construct valid Schnorr signature from a list of partial MuSig signatures."""
//assert s_list is not None and all(isinstance(s, int) for s in s_list)
//s_agg = sum(s_list) % SECP256K1_ORDER
//return R_musig.get_x().to_bytes(32, 'big') + s_agg.to_bytes(32, 'big')

fun aggregateMusigSignatures(sList : List<BigInteger>, Rmusig : )