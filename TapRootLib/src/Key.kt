import java.lang.Math.floor
import java.math.BigInteger


    fun modinv(a: BigInteger, n: BigInteger): BigInteger {
        var t1 = 0
        var t2 = 1
        var r1 = n
        var r2 = a

        while (r2 != BigInteger.ZERO) {
            var q1 = r1 / r2
            var q = floor(q1.toDouble()).toInt()
            var ttemp = t1
            t1 = t2
            t2 = ttemp - q * t2
            var rtemp = r1
            r1 = r2
            r2 = rtemp - BigInteger.valueOf(q.toLong()) * r2
        }
        if (r1 > BigInteger.ONE) {
            return null!!
        }

        if (t1 < 0) {
            return BigInteger.valueOf(t1.toLong()) + n
        }
        return BigInteger.valueOf(t1.toLong())
    }

    fun jacobi_symbol(n: BigInteger, k: BigInteger):Int {
//        For our application k is always prime, so this is the same as the Legendre symbol."""
        assert(k > BigInteger.ZERO && k.and(BigInteger.ONE) == BigInteger.ONE)  // , "jacobi symbol is only defined for positive odd k"
        var i = n % k
        var j = k
        var t = 0
//        print(i)
        while (i != BigInteger.ZERO) {
            while (i.and(BigInteger.ONE) == BigInteger.ZERO) {
                i = i.shr(1)
                var r = j.and(BigInteger.valueOf(7))
                if (r == BigInteger.valueOf(3) || r == BigInteger.valueOf(5)) {
                    t = t.xor(1)
                } else {
                    t = t.xor(0)
                }
            }
            var temp = i
            i = j
            j = temp
            temp = i.and(j.and(BigInteger.valueOf(3)))
            if (temp == BigInteger.valueOf(3)) {
                t = t.xor(1)
            } else {
                t = t.xor(0)
            }
            i = i%j
        }

        println("j: %d, t: %d".format(j,t))
        if (j == BigInteger.ONE) {
            if (t == 0) {
                return 1
            } else return 0
        }
        return 0
    }





