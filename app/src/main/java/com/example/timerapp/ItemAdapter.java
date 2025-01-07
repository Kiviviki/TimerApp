package com.example.timerapp;

import android.content.Context;
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

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
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

        // Get context from the ViewHolder's itemView
        Context context = holder.itemView.getContext();

        // Dynamically update the background based on timer state (running or stopped)
        boolean isTimerRunning = isRunning.getOrDefault(itemName, false);
        boolean isTimerEnded = timers.get(itemName).getEndTime() != null;

        // Determine background color based on timer state
        int backgroundColor;
        if (isTimerRunning) {
            // Timer is running
            backgroundColor = ContextCompat.getColor(context, R.color.RunningItemBg); // Yellow
        } else if (isTimerEnded) {
            // Timer has ended
            backgroundColor = ContextCompat.getColor(context, R.color.EndItemBg); // Green
        } else {
            // Default background
            backgroundColor = ContextCompat.getColor(context, R.color.DefaultItemBg); // Dark
        }

        // Set the background color dynamically
        holder.nameTextView.setBackgroundColor(backgroundColor);

        // Set address text to TextView
        holder.nameTextView.setText(itemName);

        // Set up a click listener to open Google Maps for this address
        holder.nameTextView.setOnClickListener(v -> {
            openAddressInGoogleMaps(itemName);
        });

        // Button setups (Start/Stop, Edit, Delete, etc.)
        holder.startStopButton.setText(isTimerRunning ? "Pysäytä" : "Aloita");

        holder.deleteButton.setOnClickListener(v -> mainActivity.deleteItem(itemName));
        holder.editButton.setOnClickListener(v -> mainActivity.editItem(itemName));

        holder.startStopButton.setOnClickListener(v -> {
            // Toggle the timer for this item
            mainActivity.toggleTimer(itemName);

            // Get the updated timer state after toggle
            boolean timerState = isRunning.get(itemName);

            // Set the correct button text based on the state of the timer
            holder.startStopButton.setText(timerState ? "Pysäytä" : "Aloita");

            // Update the background color of the name text view based on the new timer state
            if (timerState) {
                holder.nameTextView.setBackgroundColor(ContextCompat.getColor(context, R.color.RunningItemBg));
            } else if (timers.get(itemName).getEndTime() != null) {
                holder.nameTextView.setBackgroundColor(ContextCompat.getColor(context, R.color.EndItemBg));
            } else {
                holder.nameTextView.setBackgroundColor(ContextCompat.getColor(context, R.color.DefaultItemBg));
            }
        });
    }

    private void openAddressInGoogleMaps(String itemText) {
        try {
            String[] parts = itemText.split("\\s+", 2); // Split into [number, address]
            String address = parts.length > 1 ? parts[1] : itemText;

            Uri geoUri = Uri.parse("geo:0,0?q=" + Uri.encode(address));

            AlertDialog.Builder builder = new AlertDialog.Builder(mainActivity);

            String message = "Avataanko Google Maps?\n\n" + address;

            builder.setMessage(message)
                    .setCancelable(true)
                    .setPositiveButton("Kyllä", (dialog, id) -> {
                        Intent mapIntent = new Intent(Intent.ACTION_VIEW, geoUri);
                        mapIntent.setPackage("com.google.android.apps.maps");
                        mainActivity.startActivity(mapIntent);
                    })
                    .setNegativeButton("Ei", (dialog, id) -> {
                        dialog.dismiss();
                    });

            AlertDialog alert = builder.create();
            alert.show();
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
