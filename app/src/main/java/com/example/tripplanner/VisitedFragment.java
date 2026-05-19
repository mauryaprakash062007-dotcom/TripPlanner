package com.example.tripplanner;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.button.MaterialButton;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;

public class VisitedFragment extends Fragment {

    private static final String TAG = "VisitedFragment";

    String destination;
    long startDate;

    TextView tvEmpty;
    ListView listVisited;

    static class ViewHolder {
        ImageView ivImage;
        TextView tvPlaceholder;
        TextView tvName, tvCategory, tvAddress, tvRating;
        MaterialButton btnSelect, btnVisit, btnStar;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_visited, container, false);
        tvEmpty = view.findViewById(R.id.tvVisitedEmpty);
        listVisited = view.findViewById(R.id.listVisited);

        if (getArguments() != null) {
            destination = getArguments().getString("destination");
            startDate = getArguments().getLong("startDate", 0);
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (tvEmpty != null) loadVisitedPlaces();
    }

    private void loadVisitedPlaces() {
        if (getContext() == null || tvEmpty == null) return;
        ArrayList<AttractionsFragment.Place> visited = AttractionsFragment.getVisitedPlaces(getContext(), destination, startDate);
        if (visited.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            listVisited.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            listVisited.setVisibility(View.VISIBLE);
            showPlaces(visited);
        }
    }

    private void showPlaces(final ArrayList<AttractionsFragment.Place> places) {
        android.widget.BaseAdapter adapter = new android.widget.BaseAdapter() {
            @Override public int getCount()          { return places.size(); }
            @Override public Object getItem(int pos) { return places.get(pos); }
            @Override public long getItemId(int pos) { return pos; }

            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                ViewHolder h;
                if (convertView == null) {
                    convertView = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.item_attraction, parent, false);
                    h = new ViewHolder();
                    h.ivImage       = convertView.findViewById(R.id.ivAttractionImage);
                    h.tvPlaceholder = convertView.findViewById(R.id.tvAttractionImagePlaceholder);
                    h.tvName        = convertView.findViewById(R.id.tvAttractionName);
                    h.tvCategory    = convertView.findViewById(R.id.tvAttractionCategory);
                    h.tvAddress     = convertView.findViewById(R.id.tvAttractionAddress);
                    h.tvRating      = convertView.findViewById(R.id.tvAttractionRating);
                    h.btnSelect     = convertView.findViewById(R.id.btnSelect);
                    h.btnVisit      = convertView.findViewById(R.id.btnVisit);
                    h.btnStar       = convertView.findViewById(R.id.btnStar);
                    convertView.setTag(h);
                } else {
                    h = (ViewHolder) convertView.getTag();
                }

                final AttractionsFragment.Place place = places.get(position);
                h.btnStar.setVisibility(View.GONE);
                h.tvName.setText(place.name);
                h.tvCategory.setText(place.category);
                h.tvAddress.setText(place.address);
                if (place.rating >= 0) {
                    h.tvRating.setText(String.format(Locale.getDefault(), "★ %.1f", place.rating));
                    h.tvRating.setVisibility(View.VISIBLE);
                } else {
                    h.tvRating.setVisibility(View.GONE);
                }

                h.tvPlaceholder.setText(place.emoji != null ? place.emoji : "📍");

                if (place.imageUrl != null && !place.imageUrl.isEmpty()) {
                    h.ivImage.setVisibility(View.VISIBLE);
                    h.tvPlaceholder.setVisibility(View.GONE);

                    final ImageView imgView = h.ivImage;
                    final TextView phView = h.tvPlaceholder;

                    Glide.with(parent.getContext())
                            .load(place.imageUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .transition(DrawableTransitionOptions.withCrossFade(250))
                            .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                                @Override
                                public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e,
                                        Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                        boolean isFirstResource) {
                                    imgView.setVisibility(View.GONE);
                                    phView.setVisibility(View.VISIBLE);
                                    return true;
                                }
                                @Override
                                public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model,
                                        com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                        com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                                    return false;
                                }
                            })
                            .into(h.ivImage);
                } else {
                    h.ivImage.setVisibility(View.GONE);
                    h.tvPlaceholder.setVisibility(View.VISIBLE);
                }

                h.btnSelect.setOnClickListener(v -> openMapsNavigation(place));

                h.btnVisit.setText("Unvisit");
                h.btnVisit.setOnClickListener(v -> {
                    if (getContext() == null) return;
                    ArrayList<AttractionsFragment.Place> visited = AttractionsFragment.getVisitedPlaces(getContext(), destination, startDate);
                    for (int i = 0; i < visited.size(); i++) {
                        if (visited.get(i).name.equalsIgnoreCase(place.name)) {
                            visited.remove(i);
                            break;
                        }
                    }
                    AttractionsFragment.saveVisitedPlaces(getContext(), destination, startDate, visited);
                    loadVisitedPlaces();
                    Toast.makeText(getContext(), "Removed from Visited!", Toast.LENGTH_SHORT).show();
                });

                return convertView;
            }
        };

        listVisited.setAdapter(adapter);
    }

    private void openMapsNavigation(AttractionsFragment.Place place) {
        try {
            android.content.Context ctx = getContext();
            if (ctx == null) return;
            String query = URLEncoder.encode(place.name + " " + place.address, "UTF-8");
            Uri mapsUri = Uri.parse("google.navigation:q=" + query + "&mode=d");
            Intent intent = new Intent(Intent.ACTION_VIEW, mapsUri);
            intent.setPackage("com.google.android.apps.maps");

            if (intent.resolveActivity(ctx.getPackageManager()) != null) {
                ctx.startActivity(intent);
            } else {
                Uri webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + query);
                ctx.startActivity(new Intent(Intent.ACTION_VIEW, webUri));
            }
        } catch (Exception e) {
            Log.e(TAG, "Maps intent failed: " + e.getMessage());
        }
    }
}
