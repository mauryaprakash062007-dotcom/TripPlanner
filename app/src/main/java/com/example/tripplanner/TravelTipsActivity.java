package com.example.tripplanner;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class TravelTipsActivity extends AppCompatActivity {

    MaterialButton btnBack;

    String[] tips = {
        "🧳 Pack light – aim for a carry-on only to save time and money.",
        "💳 Always carry a backup card and some local cash.",
        "📸 Take photos of your passport, tickets, and hotel address.",
        "🌐 Download offline maps before travelling to avoid data charges.",
        "🔌 Carry a universal travel adapter for international trips.",
        "💉 Check vaccination requirements at least 4–6 weeks before departure.",
        "🏥 Always carry travel insurance – it's worth it!",
        "⏰ Arrive at the airport at least 2 hours early for domestic, 3 for international.",
        "🎒 Use packing cubes to keep your luggage organized.",
        "📱 Turn on airplane mode and use Wi-Fi to save on roaming charges.",
        "🍽️ Try local street food – it's often the most authentic and affordable.",
        "🗺️ Learn a few phrases in the local language – locals appreciate the effort.",
        "🌙 Book accommodation in advance, especially during peak seasons.",
        "☀️ Always carry sunscreen and stay hydrated, especially in tropical climates.",
        "🔒 Use a padlock for your luggage and a money belt for valuables."
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_travel_tips);

        btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Populate tips dynamically
        android.widget.LinearLayout container = findViewById(R.id.tipsContainer);
        for (int i = 0; i < tips.length; i++) {
            androidx.cardview.widget.CardView card = new androidx.cardview.widget.CardView(this);
            android.widget.LinearLayout.LayoutParams cardParams = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, 16);
            card.setLayoutParams(cardParams);
            card.setRadius(16);
            card.setCardElevation(0);
            card.setCardBackgroundColor(getResources().getColor(R.color.surface_dark, null));

            TextView tv = new TextView(this);
            tv.setText((i + 1) + ". " + tips[i]);
            tv.setTextColor(getResources().getColor(R.color.text_primary, null));
            tv.setTextSize(14);
            tv.setPadding(40, 32, 40, 32);
            tv.setLineSpacing(4, 1.2f);

            card.addView(tv);
            container.addView(card);
        }
    }
}
