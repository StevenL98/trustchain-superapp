package com.example.musicdao.playlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.example.musicdao.MusicBaseFragment
import com.example.musicdao.MusicService
import com.example.musicdao.wallet.WalletService
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import nl.tudelft.trustchain.common.R
import nl.tudelft.trustchain.common.ui.TabsAdapter

class VotesFragment : MusicBaseFragment(R.layout.fragment_votes) {

    private lateinit var tabsAdapter: TabsAdapter
    private lateinit var viewPager: ViewPager2

    private val TAB_NAMES = arrayOf("Upvotes", "Downvotes", "Undecided votes")

    // initialize voters with 0 pro, 0 against and 2 undecided votes
    private val voters =
        hashMapOf(0 to arrayListOf(), 1 to arrayListOf(), 2 to arrayListOf("User1", "User2"))

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_votes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        tabsAdapter = TabsAdapter(this, voters)
        viewPager = view.findViewById(R.id.viewpager)
        viewPager.adapter = tabsAdapter

        val localArgs = arguments
        if (localArgs is Bundle) {
            val artists = localArgs.getString("artists", "Artists not found")
            val price = localArgs.getString("amount", "Price not found") + "BTC"
            val userHasVoted = false

            view.findViewById<TextView>(R.id.title).text = artists
            view.findViewById<TextView>(R.id.price).text = getString(R.string.bounty_payout, price, artists)
            val favorVotes = voters[0]!!.size
            val againstVotes = voters[1]!!.size
            val undecidedVotes = voters[2]!!.size
            view.findViewById<ExtendedFloatingActionButton>(R.id.fab_user).setOnClickListener { v ->
                val builder = AlertDialog.Builder(v.context)
                builder.setTitle(getString(R.string.bounty_payout, price, artists))
                builder.setMessage(
                    getString(
                        R.string.bounty_payout_message,
                        price,
                        artists,
                        favorVotes,
                        againstVotes,
                        undecidedVotes
                    )
                )
                builder.setPositiveButton("YES") { _, _ ->
                    Toast.makeText(
                        v.context, getString(
                            R.string.bounty_payout_upvoted,
                            price,
                            artists
                        ), Toast.LENGTH_SHORT
                    ).show()
                    voters[0]!!.add("User1")
                    voters[2]!!.remove("User1")
                    userHasAlreadyVoted(view)
                    tabsAdapter = TabsAdapter(this, voters)
                    viewPager.adapter = tabsAdapter
                    viewPager.currentItem = 0
                    checkIfAllVoted(v)
                }

                builder.setNeutralButton("NO") { _, _ ->
                    Toast.makeText(
                        v.context, getString(
                            R.string.bounty_payout_downvoted,
                            price,
                            artists
                        ), Toast.LENGTH_SHORT
                    ).show()
                    voters[1]!!.add("User1")
                    voters[2]!!.remove("User1")
                    userHasAlreadyVoted(view)
                    tabsAdapter = TabsAdapter(this, voters)
                    viewPager.adapter = tabsAdapter
                    viewPager.currentItem = 1
                    checkIfAllVoted(v)

                    val walletDir = context?.cacheDir ?: throw Error("CacheDir not found")
                    val walletService = WalletService.getInstance(walletDir, (activity as MusicService))
                    val result = walletService.signWalletOwner()
                    if (result) {
                        findNavController().navigateUp()
                    }
                }
                builder.show()
            }

            // FOR THE DEMO TOMORROW
            view.findViewById<ExtendedFloatingActionButton>(R.id.fab_demo).setOnClickListener { v ->
                val builder = AlertDialog.Builder(v.context)
                builder.setTitle(getString(R.string.bounty_payout, price, artists))
                builder.setMessage(
                    getString(
                        R.string.bounty_payout_message,
                        price,
                        artists,
                        favorVotes,
                        againstVotes,
                        undecidedVotes
                    )
                )
                builder.setPositiveButton("YES") { _, _ ->
                    Toast.makeText(
                        v.context, getString(
                            R.string.bounty_payout_upvoted,
                            price,
                            artists
                        ), Toast.LENGTH_SHORT
                    ).show()
                    voters[0]!!.add("User2")
                    voters[2]!!.remove("User2")
                    view.findViewById<ExtendedFloatingActionButton>(R.id.fab_demo).visibility = View.GONE
                    tabsAdapter = TabsAdapter(this, voters)
                    viewPager.adapter = tabsAdapter
                    viewPager.currentItem = 0
                    checkIfAllVoted(v)
                }

                builder.setNeutralButton("NO") { _, _ ->
                    Toast.makeText(
                        v.context, getString(
                            R.string.bounty_payout_downvoted,
                            price,
                            artists
                        ), Toast.LENGTH_SHORT
                    ).show()
                    voters[1]!!.add("User2")
                    voters[2]!!.remove("User2")
                    view.findViewById<ExtendedFloatingActionButton>(R.id.fab_demo).visibility = View.GONE
                    tabsAdapter = TabsAdapter(this, voters)
                    viewPager.adapter = tabsAdapter
                    viewPager.currentItem = 1
                    checkIfAllVoted(v)

                    val walletDir = context?.cacheDir ?: throw Error("CacheDir not found")
                    val walletService = WalletService.getInstance(walletDir, (activity as MusicService))
                    val result = walletService.signwalletUser()
                    if (result) {
                        findNavController().navigateUp()
                    }
                }
                builder.show()
            }

            if (userHasVoted) {
                userHasAlreadyVoted(view)
            }
        }

        val tabLayout = view.findViewById<TabLayout>(R.id.tab_layout)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = TAB_NAMES[position]
        }.attach()
    }

    private fun checkIfAllVoted(v: View) {
        if (voters[2]!!.size == 0) {
            Toast.makeText(
                v.context,
                getString(R.string.bounty_payout_all_voted),
                Toast.LENGTH_LONG
            ).show()
            findNavController().navigateUp()
        }
    }

    private fun userHasAlreadyVoted(view: View) {
        view.findViewById<ExtendedFloatingActionButton>(R.id.fab_user).visibility = View.GONE
    }
}
