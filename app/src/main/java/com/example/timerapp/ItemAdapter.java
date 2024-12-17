package com.example.timerapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashMap;

public class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.ItemViewHolder> {

    private final MainActivity mainActivity;
    private final HashMap<String, TimeDetails> timers;
    private final HashMap<String, Boolean> isRunning;
    private final HashMap<String, Handler> handlers;
    private final ArrayList<String> items;

    public ItemAdapter(MainActivity mainActivity, ArrayList<String> items, HashMap<String, TimeDetails> timers, HashMap<String, Boolean> isRunning, HashMap<String, Handler> handlers) {
        this.mainActivity = mainActivity;
        this.items = items;
        this.timers = timers;
        this.isRunning = isRunning;
        this.handlers = handlers;
    }

    @Override
    public ItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_layout, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ItemViewHolder holder, int position) {
        String itemName = items.get(position);

        // Set address text to TextView
        holder.nameTextView.setText(itemName);

        // Set up a click listener to open Google Maps for this address
        holder.nameTextView.setOnClickListener(v -> {
            openAddressInGoogleMaps(itemName);
        });

        // Button setups (Start/Stop, Edit, Delete, etc.)
        holder.startStopButton.setText(isRunning.get(itemName) ? "Pysäytä" : "Aloita");

        holder.deleteButton.setOnClickListener(v -> mainActivity.deleteItem(itemName));
        holder.editButton.setOnClickListener(v -> mainActivity.editItem(itemName));

        holder.startStopButton.setOnClickListener(v -> {
            mainActivity.toggleTimer(itemName);

            boolean timerState = isRunning.get(itemName);
            holder.startStopButton.setText(timerState ? "Pysäytä" : "Aloita");
        });
    }

    private void openAddressInGoogleMaps(String itemText) {
        try {
            // Extract the address part by splitting at the first space or tab
            String[] parts = itemText.split("\\s+", 2); // Split into [number, address]
            String address = parts.length > 1 ? parts[1] : itemText; // Default to full text if split fails

            // Create a geo URI with the extracted address
            Uri geoUri = Uri.parse("geo:0,0?q=" + Uri.encode(address));

            // Create an implicit intent to open Google Maps
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, geoUri);
            mapIntent.setPackage("com.google.android.apps.maps");  // Target Google Maps app
            mainActivity.startActivity(mapIntent);
        } catch (Exception e) {
            Log.e("GoogleMapsError", "Failed to open address in Google Maps", e);
        }
    }


    @Override
    public int getItemCount() {
        Log.d("AdapterDebug", "Items count: " + items.size());
        return items.size();
    }

    public static class ItemViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView;
        Button startStopButton;
        Button deleteButton;
        Button editButton;

        public ItemViewHolder(View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.nameTextView);
            startStopButton = itemView.findViewById(R.id.startStopButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
            editButton = itemView.findViewById(R.id.editButton);
        }
    }
}
