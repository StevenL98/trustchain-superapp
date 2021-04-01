import java.math.BigInteger
import kotlin.math.pow


/**
 * A cryptographic elliptical curve to preform cryptography on.
 */
abstract class EcCurve {
    /**
     * The prime modulus of the curve.
     */
    abstract val p : BigInteger

    /**
     * The prime order of the curve.
     */
    abstract val n : BigInteger

    /**
     * The a coefficient of the curve.
     */
    abstract val a : BigInteger

    /**
     * The b coefficient of the curve.
     */
    abstract val b : BigInteger

    /**
     * X cord of the generator point -> G.
     */
    abstract val x : BigInteger

    /**
     * Y cord of the generator point -> G.
     */
    abstract val y : BigInteger

    /**
     * The generator point of the curve.
     */
    val g : EcPoint
        get() = EcPoint(x, y, BigInteger.ZERO, this)

    /**
     * The identify of the curve.
     *
     * (PRIME MODULUS, 0)
     */
    val identity : EcPoint
        get() = PointMath.identity(g)

    /**
     * Adds two points that belong to the curve.
     *
     * @param p1 The first point.
     * @param p2 The second point.
     * @return The sum of the two points.
     */
    fun add (p1 : EcPoint, p2: EcPoint) : EcPoint {

        val u1 = (p1.x*p2.z*p2.z) % this.p
        val u2 = (p2.x*p1.z*p1.z) % this.p
        val s1 = (p1.y*p2.z*p2.z*p2.z) % this.p
        val s2 = (p2.y*p1.z*p1.z*p1.z) % this.p
        if (u1 == u2) {
            if (s1 != s2) {
                return null!!
            }
            else {
                return double(p1)
            }
        }
        val h = u2-u1
        val r = s2-s1
        val x3 = ((r*r) - (h*h*h) - (BigInteger.TWO*u1*h*h)) % this.p
        val y3 = (r*(u1*h*h - x3) - (s1*h*h*h)) % this.p
        val z3 = (h*p1.z*p2.z) % this.p
        return EcPoint(x3,y3,z3,p1.curve)

    }

    /**
     * Finds the product of a point on the curve. (Scalar multiplication)
     *
     * @param g The generator point to start at.
     * @param n The number of times to dot the curve from g.
     * @return The point ended up on the curve.
     */
    fun multiply (g : EcPoint, n : BigInteger) : EcPoint {
        var r = identity
        var q = g
        var m = n

        while (m != EcConstants.ZERO) {


            if (m and EcConstants.ONE != 0.toBigInteger()) {
                r = add(r, q)
            }

            m = m shr 1

            if (m != 0.toBigInteger()) {
                q = PointMath.double(q)
            }

        }

        return r
    }

    /**
     * Adds two points that belong to the curve.
     *
     * @param point The point to add to the g point.
     * @return The sum of the two points.
     */
    operator fun plus (point : EcPoint) : EcPoint {
        return add(g, point)
    }

    /**
     * Finds the product of a point on the curve and its generator point. (Scalar multiplication)
     *
     * @param n The number of times to dot the curve from g.
     * @return The product of the point.
     */
    operator fun times(n : BigInteger) : EcPoint {
        return multiply(g, n)
    }

    fun affine(point : EcPoint) : EcPoint {
        if (point.z == BigInteger.ZERO) {
            return null!!
        }
        val inv = modinv(point.z, this.p)
        val inv2 = BigInteger.valueOf(Math.pow(inv.toDouble(), 2.0).toLong()) % this.p
        val inv3 = (inv2 * inv) % this.p
        return (EcPoint((inv2*point.x)%this.p, (inv3*point.y)%this.p, BigInteger.ONE, point.curve ))
    }

    fun negate(point : EcPoint) : EcPoint {
        return(EcPoint(point.x,(this.p-point.y)%this.p,point.z,point.curve))
    }

    fun onCurve(p1: EcPoint):Boolean {
        var z2 = (p1.z * p1.z) % this.p
        var z4 = (z2*z2) % this.p
        return(p1.z != BigInteger.ZERO &&
                ((BigInteger.valueOf(Math.pow(p1.x.toDouble(),3.0).toLong()) % this.p) + this.x*p1.x*z4 + this.b*z2*z4 -
                        BigInteger.valueOf(Math.pow(p1.y.toDouble(), 2.0).toLong())%this.p) % this.p == BigInteger.ZERO)
    }

    fun isXCoordinate(x: BigInteger): Boolean {
        val x3 : BigInteger = x.toDouble().pow(3.0).toBigDecimal().toBigInteger()%this.p
        return jacobi_symbol(x3 + this.a * x + this.b, this.p) != -1
    }

    fun double(point: EcPoint): EcPoint {
        if (point.z == BigInteger.ZERO) {
            return EcPoint(BigInteger.ZERO, BigInteger.ONE, BigInteger.ZERO, point.curve)
        }
        val y2 = (point.y * point.y) % this.p
        val y4 = (y2 * y2) % this.p
        val x2 = (point.x * point.x) % this.p
        val z4 = (point.z * point.z * point.z * point.z) % this.p
        val s = (BigInteger.valueOf(4)*point.x*y2)  % this.p
        val m =(BigInteger.valueOf(3)*x2 + this.a * z4) % this.p
        val xprime = (m*m - BigInteger.TWO*s) % this.p
        val yprime = (m*(s-xprime) - BigInteger.valueOf(8)*y4) % this.p
        val zprime = (BigInteger.TWO* point.y * point.z) %this.p
        return EcPoint(xprime, yprime, zprime, point.curve)
    }
}




