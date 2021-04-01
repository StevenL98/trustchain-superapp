import hash.EcHasher
import java.math.BigInteger
import java.security.SecureRandom

object SchnorrSign {

    private fun getRandomK (n : BigInteger) : BigInteger {
        val randomValue = BigInteger(256, SecureRandom())

        if (randomValue >= n || randomValue <= BigInteger.ONE) {
            return getRandomK(n)
        }

        return randomValue
    }

    fun signData (keyPair: EcKeyPair, data : ByteArray, hasher : EcHasher) : EcSignature {
        // todo range from 1 to n-1
        val hash = BigInteger(1, hasher.hash(data))
        val g = keyPair.publicKey.curve.g
        val n = keyPair.publicKey.curve.n
        val k = getRandomK(n) % n
        val p1 = g * k
        val r = p1.x

        if (r == EcConstants.ZERO) {
            signData(keyPair, data, hasher)
        }
        val s = (k.modInverse(n)) * (hash + (keyPair.privateKey * r) % n) % n

        if (s == EcConstants.ZERO) {
            signData(keyPair, data, hasher)
        }

        return EcSignature(r, s)
    }
}