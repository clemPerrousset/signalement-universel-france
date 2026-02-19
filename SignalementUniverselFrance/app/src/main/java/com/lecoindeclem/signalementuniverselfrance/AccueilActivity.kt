package com.lecoindeclem.signalementuniverselfrance

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat

class AccueilActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accueil)

        // Licenses link
        findViewById<TextView>(R.id.tvLicenses).setOnClickListener {
            showLicensesDialog()
        }

        // Card 1: Mairie
        findViewById<View>(R.id.cardMairie).setOnClickListener { view ->
            val intent = Intent(this, MainActivity::class.java)
            val options = ActivityOptionsCompat.makeScaleUpAnimation(
                view, 0, 0, view.width, view.height
            )
            startActivity(intent, options.toBundle())
        }

        // Card 2: Internet
        findViewById<View>(R.id.cardInternet).setOnClickListener {
            openUrl("https://www.internet-signalement.gouv.fr")
        }

        // Card 3: Produit
        findViewById<View>(R.id.cardProduit).setOnClickListener { view ->
            val intent = Intent(this, SignalementProduitActivity::class.java)
            val options = ActivityOptionsCompat.makeScaleUpAnimation(
                view, 0, 0, view.width, view.height
            )
            startActivity(intent, options.toBundle())
        }

        // Card 4: SMS
        findViewById<View>(R.id.cardSms).setOnClickListener {
            sendSms("33700")
        }

        // Card 5: Nature
        findViewById<View>(R.id.cardNature).setOnClickListener {
            openUrl("https://sentinelles.sportsdenature.fr/")
        }
    }

    private fun showLicensesDialog() {
        val message = Html.fromHtml(getString(R.string.license_dialog_content), Html.FROM_HTML_MODE_COMPACT)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.license_dialog_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .create()

        dialog.show()
        // Make links clickable
        dialog.findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendSms(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
