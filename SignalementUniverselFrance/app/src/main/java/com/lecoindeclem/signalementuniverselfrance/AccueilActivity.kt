package com.lecoindeclem.signalementuniverselfrance

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class AccueilActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_accueil)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
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
