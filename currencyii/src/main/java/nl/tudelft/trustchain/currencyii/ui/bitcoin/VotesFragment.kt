package nl.tudelft.trustchain.currencyii.ui.bitcoin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.common.R
import nl.tudelft.trustchain.common.ui.TabsAdapter
import nl.tudelft.trustchain.currencyii.ui.BaseFragment
import nl.tudelft.trustchain.currencyii.sharedWallet.SWTransferFundsAskTransactionData

class VotesFragment : BaseFragment(R.layout.fragment_votes) {
    private lateinit var tabsAdapter: TabsAdapter
    private lateinit var viewPager: ViewPager2

    private val TAB_NAMES = arrayOf("Upvotes", "Downvotes", "Undecided votes")

    private lateinit var voters: HashMap<Int, ArrayList<String>>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_votes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val localArgs = arguments
        if (localArgs is Bundle) {
            val userID = getTrustChainCommunity().myPeer.publicKey.keyToBin().toHex()
            val position = localArgs.getInt("position")

            val block = getCoinCommunity().fetchProposalBlocks()[position]

            val rawData = SWTransferFundsAskTransactionData(block.transaction)
            val data = rawData.getData()

            val walletId = data.SW_UNIQUE_PROPOSAL_ID
            val priceString = data.SW_TRANSFER_FUNDS_AMOUNT.toString() + " Satoshi"

            val signatures =
                ArrayList(getCoinCommunity().fetchProposalSignatures(
                    data.SW_UNIQUE_ID,
                    data.SW_UNIQUE_PROPOSAL_ID
                ))
            voters = rawData.SW_VOTES
            voters[0] = signatures
            voters[2]!!.removeAll(signatures)
            // TODO: Commented out for debugging purposes and since the userID is not present in the list of voters somehow?
            val userHasVoted = false // !voters[2]!!.contains(userID)

            view.findViewById<ExtendedFloatingActionButton>(R.id.fab_demo).visibility = View.GONE

            view.findViewById<TextView>(R.id.title).text = data.SW_UNIQUE_PROPOSAL_ID
            view.findViewById<TextView>(R.id.price).text = getString(R.string.bounty_payout, priceString, walletId)
            view.findViewById<ExtendedFloatingActionButton>(R.id.fab_user).setOnClickListener { v ->
                val builder = AlertDialog.Builder(v.context)
                builder.setTitle(getString(R.string.bounty_payout, priceString, walletId))
                builder.setMessage(getString(R.string.bounty_payout_message,
                        priceString,
                        walletId,
                        voters[0]!!.size,
                        voters[1]!!.size,
                        voters[2]!!.size
                    )
                )
                builder.setPositiveButton("YES") { _, _ ->
                    Toast.makeText(
                        v.context,
                        getString(R.string.bounty_payout_upvoted, priceString, walletId),
                        Toast.LENGTH_SHORT
                    ).show()
                    voters = SWTransferFundsAskTransactionData(block.transaction).userVotes(userID, 0)
                    userHasAlreadyVoted(view)
                    tabsAdapter = TabsAdapter(this, voters)
                    viewPager.adapter = tabsAdapter
                    viewPager.currentItem = 0

//                    val walletDir = context?.cacheDir ?: throw Error("CacheDir not found")
//                    val walletService =
//                        WalletService.getInstance(walletDir, (activity as MusicService))
//                    val result = walletService.signUser1()
//                    if (result) {
                    if (voters[2]!!.size == 0) {
                        findNavController().navigateUp()
                    }
                    // TODO: send yes vote for user1
                }

                builder.setNeutralButton("NO") { _, _ ->
                    Toast.makeText(
                        v.context,
                        getString(R.string.bounty_payout_downvoted, priceString, walletId),
                        Toast.LENGTH_SHORT
                    ).show()
                    voters = SWTransferFundsAskTransactionData(block.transaction).userVotes(userID, 1)

                    userHasAlreadyVoted(view)
                    tabsAdapter = TabsAdapter(this, voters)
                    viewPager.adapter = tabsAdapter
                    viewPager.currentItem = 1
                    // TODO: send no vote for user1
                }
                builder.show()
            }

            if (userHasVoted) {
                userHasAlreadyVoted(view)
            }
        }

        viewPager = view.findViewById(R.id.viewpager)
        tabsAdapter = TabsAdapter(this, voters)
        viewPager.adapter = tabsAdapter

        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = TAB_NAMES[position]
        }.attach()
    }

    private fun userHasAlreadyVoted(view: View) {
        view.findViewById<ExtendedFloatingActionButton>(R.id.fab_user).visibility = View.GONE
    }
}