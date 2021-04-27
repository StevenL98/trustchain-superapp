# Taproot Library

### What is Taproot?
When looking up what taproot is, you will find the following:
> Taproot is a soft fork that improves Bitcoin's scripts increase privacy and improve upon other factors related to complex transactions. Transactions on the Bitcoin network can use various features that make them more complex, including timelock releases, multi-signature requirements, and others.

However, this does seem a little bit buzz-wordy, therefore let us simplify this slightly with what we have done with our library in the scope of our project. 
We have focussed on the part of creating a multisig wallet in a way that is scalable, which is described by Taproot. 
A multisig wallet (short for multi-signature) makes it possible for multiple users to make a transaction as a group. This already works for Bitcoin, however it is done in a non-scalable way. 
That is, for each additional member of the wallet, additional information (public keys and signatures) needs to be included on the Blockchain causing it to be more expensive (as more bits = higher transaction costs).

This is where taproot comes in, they use [Schnorr signatures](https://academy.binance.com/en/articles/what-do-schnorr-signatures-mean-for-bitcoin), that can aggregate these signatures, making transactions look like they are coming from a single user, whilst they can in fact come from a wallet with 20 users in it. 



### Taproot within our project
We implemented a Taproot library for the purpose of creating multi-signature wallets for the [Trustchain Superapp](https://play.google.com/store/apps/details?id=nl.tudelft.trustchain&hl=en&gl=US). 
We made sure that with the use of taproot, the following can be done on the Trustchain with a DAO:
    
    - Make a single wallet with just one user.
    - Make a wallet with multiple users.
    - Make a proposal for a transaction.
    - Vote on these proposals and come to a consensus if a threshold is reached.
    - Execute the transaction.
    
Because BitcoinJ has not yet got the functionality for Taproot to work, we need to build on top of that. With help of our own Regtest server and RPC calls we extended the use of BitcoinJ for Taproot. The architecture of how that is done can be seen in the following diagram.

Architecture diagram.

---

## Our implementation
To achieve the taproot functionality, we have the following classes. We will then have a short description of what it does and how it is used within the scope of the project.

- [Address](#address)
- [CTransaction](#ctransaction)
- [Key](#key)
- [Messages](#messages)
- [Musig](#musig)
- [Schnorr](#schnorr)
- [SegwitAddress](#segwitaddress)
- [SegwitAddressKotlin](#SegwitAddressKotlin)


---


#### [Address](./taproot/Address.kt) 
> This class is a utility class for returning a [Segwit](#glossary) Address for our Regtest server.

#### [CTransaction](./taproot/CTransaction.kt) 
> Creates a transaction class for a musig transactions, this class collects the signatures of all witnesses and serialises them.
#### [Key](./taproot/Key.kt) 
> Simple extension of the BitcoinJ key to make it usable as a [Schnorr](#glossary-schnorr) [nonce](#glossary-nonce).
#### [Messages](./taproot/Messages.kt)
> This is a small utility class that handles serialization.
#### [Musig](./taproot/Musig.kt)
> This class is the multisig implementation which is described in the paper [https://eprint.iacr.org/2018/068.pdf](https://eprint.iacr.org/2018/068.pdf). This class handles the generating, aggregating and signing of multi-signatures. 
#### [Schnorr](./taproot/Schnorr.kt)
> This class handles the mathematical aspect of Taproot, and is used mainly by the [Musig](./taproot/Musig.kt) class for mathematical verification.
#### [SegwitAddress](./taproot/SegwitAddress.java)
> This class is a copy of SegwitAddress class from BitcoinJ. We needed to make some constructors public to be able to use witness versions other than 0.
#### [SegwitAddressKotlin](./taproot/SegwitAddressKotlin.kt)
> This utility class handles the encoding and decoding of the SegwitAddresses.

Sequence diagram.

---
		
		
## Future work or recommendations
   > Even though our taproot implementation works in our current set up. There are rooms for improvement. 
        In our implementation, we do not take into account [n-out-of-m](#glossary-n-out-of-m-transactions) signature transactions, only [n-out-of-n](#glossary-n-out-of-n-transactions) transactions.  
		
## Glossary

   - [SegWit](https://www.investopedia.com/terms/s/segwit-segregated-witness.asp#:~:text=SegWit%20is%20the%20process%20by,more%20transactions%20to%20the%20chain.)
        
        SegWit is the process by which the block size limit on a blockchain is increased by removing signature data from bitcoin transactions. When certain parts of a transaction are removed, this frees up space or capacity to add more transactions to the chain.
        
   - Schnorr
   - Nonce
   - N-out-of-N transactions
   - N-out-of-M transactions
   - BitcoinJ
   - RPC calls
   - Regtest
   
		
