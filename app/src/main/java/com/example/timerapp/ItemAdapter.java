package com.example.timerapp;

import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
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

        // Display only the item name without time details
        holder.nameTextView.setText(itemName);

        // Set button text based on the current timer state
        holder.startStopButton.setText(isRunning.get(itemName) ? "Pysäytä" : "Aloita");

        holder.deleteButton.setOnClickListener(v -> {
            mainActivity.deleteItem(itemName);
        });

        holder.editButton.setOnClickListener(v -> {
            mainActivity.editItem(itemName);
        });

        holder.startStopButton.setOnClickListener(v -> {
            mainActivity.toggleTimer(itemName);

            // Update button text after toggling
            boolean timerState = isRunning.get(itemName); // Fetch the updated state
            holder.startStopButton.setText(timerState ? "Pysäytä" : "Aloita");
        });
    }

    @Override
    public int getItemCount() {
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
