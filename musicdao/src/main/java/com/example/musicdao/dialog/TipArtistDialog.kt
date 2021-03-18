package com.example.musicdao.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import com.example.musicdao.MusicService
import com.example.musicdao.R
import com.example.musicdao.catalog.PlaylistsOverviewFragmentDirections
import com.example.musicdao.wallet.WalletService
import java.math.BigDecimal

/**
 * This dialog is shown when the user wants to send a tip to the publisher of a Release, using BTC
 * @param publicKey the Bitcoin wallet public key to send the tip to
 */
class TipArtistDialog(private val publicKey: String, private val artists: String) :
    DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(activity)
        // Get the layout inflater
        val inflater = requireActivity().layoutInflater

        val dialogView = inflater.inflate(R.layout.dialog_tip_artist, null)
        val amountAssistingText = dialogView?.findViewById<TextView>(R.id.amountAssistingText)
        Thread {
            activity?.runOnUiThread {
                amountAssistingText?.text = "coin(s)"
            }
        }.start()

        val instructionText = dialogView?.findViewById<TextView>(R.id.instructionText)
        instructionText?.text = "Sending a tip to artist(s), with public key: $publicKey"

        // Todo: Create proposal

        val amountEditText = dialogView?.findViewById<EditText>(R.id.amount)
        val walletDir = context?.cacheDir ?: throw Error("CacheDir not found")
        val walletService = WalletService.getInstance(walletDir, (activity as MusicService))

        builder.setView(dialogView)
            .setPositiveButton("Confirm") { _, _ ->
                val amount = amountEditText?.text.toString()
                walletService.createProposal(publicKey, amount)
                val action =
                    PlaylistsOverviewFragmentDirections.actionPlaylistsOverviewFragmentToVotesFragment(
                        artists,
                        amount
                    )
                findNavController().navigateUp()
                findNavController().navigate(action)
            }.setNegativeButton("Cancel") { _, _ ->
                dialog?.cancel()
            }

        return builder.create()
    }

    companion object {
        val SATS_PER_BITCOIN = BigDecimal(100_000_000)
    }
}
