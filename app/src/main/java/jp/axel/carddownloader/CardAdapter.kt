package jp.axel.carddownloader

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CardAdapter(
    private val imagesFolder: DocumentFile,
    private val cardList: List<String>,
    private val onCardClickListener: OnCardClickListener
) :
    RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    interface OnCardClickListener {
        fun onCardClicked(card: String, itemView: View)
    }

    inner class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardImageView: ImageView = itemView.findViewById(R.id.card_iv)

        fun bind(card: String) {
            CoroutineScope(Dispatchers.Default).launch {
                val cardPicture = imagesFolder.findFile("$card.jpg")
                CoroutineScope(Dispatchers.Main).launch {
                    if (cardPicture != null) {
                        Picasso.get().load(cardPicture.uri).resize(271, 395).into(cardImageView)
                    } else
                        cardImageView.setImageResource(R.drawable.blank_card)
                }
            }
            itemView.setOnClickListener {
                onCardClickListener.onCardClicked(card, itemView)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.card_item, parent, false)
        return CardViewHolder(itemView)
    }

    override fun getItemCount(): Int =
        cardList.size

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val card = cardList[position]
        holder.bind(card)
    }
}