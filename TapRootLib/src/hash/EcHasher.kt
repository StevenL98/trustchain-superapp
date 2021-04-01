package hash

interface EcHasher {
    fun hash (data : ByteArray) : ByteArray
}