package jp.axel.carddownloader

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import android.util.Log


private const val OPEN_DECK_REQUEST_CODE = 632
private const val OPEN_FOLDER_REQUEST_CODE = 236

class MainActivity : AppCompatActivity(), CardAdapter.OnCardClickListener {
    private lateinit var busyDialog: AlertDialog
    private lateinit var saveFolder: DocumentFile
    private lateinit var apiService: ApiService
    private lateinit var retrofit: Retrofit
    private lateinit var loadedDeck: Uri
    private var mainCards = emptyList<String>()
    private var extraCards = emptyList<String>()
    private var sideCards = emptyList<String>()
    private lateinit var mainTv: TextView
    private lateinit var extraTv: TextView
    private lateinit var sideTv: TextView
    private lateinit var loadButton: MaterialButton
    private lateinit var deckLayout: LinearLayout
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var downloadPB: ProgressBar
    private lateinit var downloadTv: TextView
    private lateinit var mainRv: RecyclerView
    private lateinit var extraRv: RecyclerView
    private lateinit var sideRv: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mainTv = findViewById(R.id.main_deck_tv)
        extraTv = findViewById(R.id.extra_deck_tv)
        sideTv = findViewById(R.id.side_deck_tv)
        loadButton = findViewById(R.id.load_deck_button)
        deckLayout = findViewById(R.id.deck_layout)
        mainRv = findViewById(R.id.main_rv)
        extraRv = findViewById(R.id.extra_rv)
        sideRv = findViewById(R.id.side_rv)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val saveFolderUri = sharedPreferences.getString("save_folder", "")!!
        if (saveFolderUri.isNotEmpty())
            saveFolder = DocumentFile.fromTreeUri(this, Uri.parse(saveFolderUri))!!
        else
            showSetSaveLocationDialog()

        val baseUrl = "https://images.ygoprodeck.com/images/cards/"
        retrofit =
            Retrofit.Builder().baseUrl(baseUrl).addConverterFactory(GsonConverterFactory.create())
                .build()
        apiService = retrofit.create(ApiService::class.java)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == OPEN_DECK_REQUEST_CODE) {
                loadedDeck = intent!!.data!!
                contentResolver.openInputStream(loadedDeck)!!.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { bufferedReader ->
                        val content = bufferedReader.readLines()
                        mainCards =
                            content.subList(content.indexOf("#main") + 1, content.indexOf("#extra"))
                        extraCards =
                            content.subList(content.indexOf("#extra") + 1, content.indexOf("!side"))
                        sideCards = content.subList(content.indexOf("!side"), content.size - 1)
                        mainTv.text = "Main - ${mainCards.size} cards"
                        extraTv.text = "Extra - ${extraCards.size} cards"
                        sideTv.text = "Side - ${sideCards.size} cards"
                        supportActionBar?.subtitle = Utils.fileNameFromUri(loadedDeck)
                        loadButton.visibility = View.GONE
                        deckLayout.visibility = View.VISIBLE
                        var imagesFolder = saveFolder.findFile("images")
                        if (imagesFolder == null)
                            imagesFolder = saveFolder.createDirectory("images")!!
                        mainRv.adapter = CardAdapter(imagesFolder, mainCards, this@MainActivity)
                        extraRv.adapter = CardAdapter(imagesFolder, extraCards, this@MainActivity)
                        sideRv.adapter = CardAdapter(imagesFolder, sideCards, this@MainActivity)
                    }
                }
            } else if (requestCode == OPEN_FOLDER_REQUEST_CODE) {
                val saveFolderUri = intent!!.data!!
                contentResolver.takePersistableUriPermission(
                    saveFolderUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                saveFolder = DocumentFile.fromTreeUri(this, saveFolderUri)!!
                sharedPreferences.edit().putString("save_folder", saveFolderUri.toString()).apply()
            }
        }
    }

    fun loadDeck(view: View?) {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*"),
            OPEN_DECK_REQUEST_CODE
        )
    }

    fun downloadDeckImages(view: View) {
        if (Utils.missingCards.size < 1)
            return
        downloadImages(Utils.missingCards.toList())
    }

    fun downloadImages(cards: List<String>) {
        var imagesFolder = saveFolder.findFile("images")
        if (imagesFolder == null)
            imagesFolder = saveFolder.createDirectory("images")!!
        //val allCards = mainCards + extraCards + sideCards
        showBusyDialog()
        downloadPB.max = cards.size
        downloadTv.text = "Downloading 1/${cards.size}"
        CoroutineScope(Dispatchers.Default).launch {
            var progress = 1
            cards.forEach { card ->
                val getCall = apiService.downloadCard(card)!!
                try {
                    val response = getCall.execute()
                    if (response.isSuccessful && response.body() != null) {
                        val content = response.body()!!.byteStream()
                        val fileName = "$card.jpg"
                        val imageFile = imagesFolder.createFile("image/*", fileName)
                        Utils.copy(content, contentResolver.openOutputStream(imageFile!!.uri)!!)
                        Utils.missingCards.remove(card)
                    }
                } catch (e: IOException) {
                }
                progress++
                CoroutineScope(Dispatchers.Main).launch {
                    downloadPB.progress = progress
                    downloadTv.text = "Downloading $progress/${cards.size}"
                }
            }
            CoroutineScope(Dispatchers.Main).launch {
                busyDialog.cancel()
                mainRv.adapter = CardAdapter(imagesFolder, mainCards, this@MainActivity)
                extraRv.adapter = CardAdapter(imagesFolder, extraCards, this@MainActivity)
                sideRv.adapter = CardAdapter(imagesFolder, sideCards, this@MainActivity)
                if (Utils.missingCards.size > 0)
                    Toast.makeText(
                        this@MainActivity,
                        Utils.missingCards.size.toString() + " image(s) couldn't be downloaded",
                        Toast.LENGTH_LONG
                    ).show()
                Log.e("miss", Utils.missingCards.toString())
            }
        }
    }

    private fun showBusyDialog() {
        val dialogBuilder = MaterialAlertDialogBuilder(this)
        val view = layoutInflater.inflate(R.layout.dialog_download_progress, null)
        downloadTv = view.findViewById(R.id.download_tv)
        downloadPB = view.findViewById(R.id.download_pb)
        dialogBuilder.setView(view)
        dialogBuilder.setCancelable(false)
        busyDialog = dialogBuilder.show()
    }

    private fun showSetSaveLocationDialog() {
        val dialogBuilder = MaterialAlertDialogBuilder(this)
        dialogBuilder.setTitle("Save location")
        dialogBuilder.setMessage("Please select a folder to save card pictures in")
        dialogBuilder.setPositiveButton("Select folder") { _, _ -> setSaveLocation() }
        dialogBuilder.setCancelable(false)
        dialogBuilder.show()
    }

    private fun setSaveLocation() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
            OPEN_FOLDER_REQUEST_CODE
        )
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_load_deck -> loadDeck(null)
            R.id.action_set_save_folder -> setSaveLocation()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCardClicked(card: String, itemView: View) {
        if (Utils.missingCards.contains(card)) {
            downloadImages(listOf(card))
        } else {
            val intent: Intent =
                Intent(this, CardDetailsActivity::class.java).putExtra("card", card)
            val options = ActivityOptionsCompat.makeSceneTransitionAnimation(this, itemView, "fade")
            startActivity(intent, options.toBundle())
        }
    }
}