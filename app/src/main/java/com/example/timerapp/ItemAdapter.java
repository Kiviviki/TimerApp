package com.example.timerapp;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
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

        // Get the colors from resources
        int runningColor = ContextCompat.getColor(context, R.color.RunningItemBg); // Yellow
        int endColor = ContextCompat.getColor(context, R.color.EndItemBg); // Green
        int defaultColor = ContextCompat.getColor(context, R.color.DefaultItemBg); // Dark
        int redColor = ContextCompat.getColor(context, R.color.redFadeColor); // Red

        // Dynamically update the background based on timer state
        boolean isTimerRunning = isRunning.getOrDefault(itemName, false);
        boolean isTimerEnded = timers.get(itemName).getEndTime() != null;

        // Set initial background color
        if (isTimerRunning) {
            startBackAndForthAnimation(holder.nameTextView, redColor, runningColor);
        } else if (isTimerEnded) {
            stopAnimation(holder.nameTextView);
            holder.nameTextView.setBackgroundColor(endColor); // Static green
        } else {
            stopAnimation(holder.nameTextView);
            holder.nameTextView.setBackgroundColor(defaultColor); // Static default
        }

        // Set the item text
        holder.nameTextView.setText(itemName);

        // Attach delete functionality to the trash can icon
        holder.deleteButton.setOnClickListener(v -> mainActivity.deleteItem(itemName));

        // Click listener for toggling the timer
        holder.nameTextView.setOnClickListener(v -> mainActivity.toggleTimer(itemName));

        // Long-click listener for editing the item
        holder.nameTextView.setOnLongClickListener(v -> {
            mainActivity.editItem(itemName);
            return true;
        });
    }


    /**
     * Starts a back-and-forth color animation on a TextView.
     *
     * @param view        The TextView to animate.
     * @param startColor  The first color.
     * @param endColor    The second color.
     */
    private void startBackAndForthAnimation(View view, int startColor, int endColor) {
        Object tag = view.getTag(R.id.animation_running); // Use a custom tag to track animations
        if (tag instanceof ObjectAnimator && ((ObjectAnimator) tag).isRunning()) {
            return; // Animation is already running
        }

        ObjectAnimator colorAnimator = ObjectAnimator.ofArgb(view, "backgroundColor", startColor, endColor);
        colorAnimator.setDuration(1000); // 1 second per phase
        colorAnimator.setRepeatMode(ValueAnimator.REVERSE); // Reverse back and forth
        colorAnimator.setRepeatCount(ValueAnimator.INFINITE); // Run forever
        colorAnimator.start();
        view.setTag(R.id.animation_running, colorAnimator); // Store the animator in the tag
    }

    /**
     * Stops any ongoing animation on a TextView.
     *
     * @param view The TextView whose animation should be stopped.
     */
    private void stopAnimation(View view) {
        Object tag = view.getTag(R.id.animation_running);
        if (tag instanceof ObjectAnimator) {
            ((ObjectAnimator) tag).cancel(); // Cancel the animation
            view.setTag(R.id.animation_running, null); // Clear the tag
        }
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
        ImageButton deleteButton;
        Button editButton;

        public ItemViewHolder(View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.nameTextView);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}
