package jp.axel.carddownloader

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.ImageView
import androidx.documentfile.provider.DocumentFile

class CardDetailsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_details)
        val card = intent.getStringExtra("card")
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val saveFolderUri = sharedPreferences.getString("save_folder", "")!!
        val saveFolder = DocumentFile.fromTreeUri(this, Uri.parse(saveFolderUri))!!
        val imagesFolder = saveFolder.findFile("images")!!
        val imageFile = imagesFolder.findFile("$card.jpg")
        val cardIv: ImageView = findViewById(R.id.card_iv)
        if (imageFile != null) {
            cardIv.setImageURI(imageFile.uri)
        }
    }

    fun shareCard(view: View) {}
}