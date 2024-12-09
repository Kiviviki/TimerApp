package com.example.timerapp;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler; // Correct import
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    private EditText textInput;
    private Button addButton;
    private RecyclerView recyclerView;
    private TextView timeDisplay;
    private ArrayList<String> items;
    private HashMap<String, TimeDetails> timers;
    private HashMap<String, Boolean> isRunning;
    private HashMap<String, Handler> handlers;  // Correct Handler type (android.os.Handler)
    private ItemAdapter itemAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textInput = findViewById(R.id.textInput);
        addButton = findViewById(R.id.addButton);
        recyclerView = findViewById(R.id.recyclerView);
        timeDisplay = findViewById(R.id.timeDisplay);

        items = new ArrayList<>();
        timers = new HashMap<>();
        isRunning = new HashMap<>();
        handlers = new HashMap<>();

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        itemAdapter = new ItemAdapter(this, items, timers, isRunning, handlers);
        recyclerView.setAdapter(itemAdapter);

        addButton.setOnClickListener(v -> {
            String text = textInput.getText().toString().trim();
            if (!text.isEmpty() && !items.contains(text)) {
                items.add(text);
                timers.put(text, new TimeDetails()); // Set start time as null initially
                isRunning.put(text, false);
                handlers.put(text, new Handler());
                itemAdapter.notifyDataSetChanged();
                textInput.setText("");
                updateTimeDisplay();
            } else if (text.isEmpty()) {
                Toast.makeText(this, "Syötä jotain järkevää.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Tämänniminen kohde löytyy jo", Toast.LENGTH_SHORT).show();

            }
        });
    }


    public void toggleTimer(String itemName) {
        if (isRunning.get(itemName)) {
            isRunning.put(itemName, false);
            handlers.get(itemName).removeCallbacksAndMessages(null);
            timers.get(itemName).setEndTime(new Date()); // Set end time when the timer stops
            Toast.makeText(this, itemName + " stopped.", Toast.LENGTH_SHORT).show();
        } else {
            isRunning.put(itemName, true);
            TimeDetails timeDetails = timers.get(itemName);

            // Set the start time to current time when starting the timer
            if (timeDetails.getStartTime() == null) {
                timeDetails.setStartTime(new Date()); // Set current time when starting
            }

            startTimer(itemName);
            Toast.makeText(this, itemName + " started", Toast.LENGTH_SHORT).show();
        }
        updateTimeDisplay();
    }


    private void startTimer(String itemName) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (isRunning.get(itemName)) {
                    handlers.get(itemName).postDelayed(this, 1000); // Repeat the task every 1 second
                }
            }
        };
        handlers.get(itemName).post(runnable);
    }

    public void deleteItem(String itemName) {
        // Show confirmation dialog before deleting
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Kohteen poisto");
        builder.setMessage("Haluatko varmasti poistaa \"" + itemName + "\"?");

        // "Yes" Button
        builder.setPositiveButton("Kyllä", (dialog, which) -> {
            // Stop the timer if it's running
            if (isRunning.get(itemName)) {
                handlers.get(itemName).removeCallbacksAndMessages(null); // Stop the Handler tasks
                isRunning.put(itemName, false); // Mark as stopped
            }

            // Remove the item from all data structures
            items.remove(itemName);
            timers.remove(itemName);
            isRunning.remove(itemName);
            handlers.remove(itemName);

            // Notify adapter and update display
            itemAdapter.notifyDataSetChanged();
            updateTimeDisplay();

            Toast.makeText(this, "\"" + itemName + "\" deleted successfully.", Toast.LENGTH_SHORT).show();
        });

        // "No" Button
        builder.setNegativeButton("Ei", (dialog, which) -> {
            // Simply dismiss the dialog
            dialog.dismiss();
        });

        // Show the dialog
        builder.show();
    }



    public void editItem(String itemName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Muokkaa kohteen nimeä");

        final EditText input = new EditText(this);
        input.setText(itemName);
        builder.setView(input);

        builder.setPositiveButton("Tallenna", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty() && !items.contains(newName)) {
                boolean wasRunning = isRunning.get(itemName);

                // Stop the timer if it's running
                if (wasRunning) {
                    handlers.get(itemName).removeCallbacksAndMessages(null);
                    isRunning.put(itemName, false);
                }

                // Update the name in `items`
                int index = items.indexOf(itemName);
                items.set(index, newName);

                // Update `timers` map
                TimeDetails details = timers.remove(itemName);
                timers.put(newName, details);

                // Update `isRunning` map
                Boolean runningState = isRunning.remove(itemName);
                isRunning.put(newName, runningState);

                // Update `handlers` map
                Handler handler = handlers.remove(itemName);
                handlers.put(newName, handler);

                itemAdapter.notifyDataSetChanged();
                updateTimeDisplay();

                // Restart the timer if it was running
                if (wasRunning) {
                    isRunning.put(newName, true);
                    startTimer(newName); // Restart the timer with the new name
                }
            } else {
                Toast.makeText(this, "Tämänniminen kohde löytyy jo.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Hylkää", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void updateTimeDisplay() {
        SpannableStringBuilder displayText = new SpannableStringBuilder();
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");

        for (String name : items) {
            TimeDetails timeDetails = timers.get(name);

            // If startTime is null, show "00:00"
            String startTime = (timeDetails.getStartTime() != null) ? dateFormat.format(timeDetails.getStartTime()) : "00:00";
            String endTime = (timeDetails.getEndTime() != null) ? dateFormat.format(timeDetails.getEndTime()) : "00:00";

            // Create SpannableString to style the start time and end time with colors
            SpannableString startTimeSpan = new SpannableString("Aloitus: " + startTime);
            SpannableString endTimeSpan = new SpannableString("Lopetus: " + endTime);

            // Set custom colors for Start and End times using ContextCompat.getColor
            startTimeSpan.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.startTimeColor)), 0, startTimeSpan.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            endTimeSpan.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.endTimeColor)), 0, endTimeSpan.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            // Append formatted text with colored start and end times to SpannableStringBuilder
            displayText.append(name)
                    .append(" - ")
                    .append(startTimeSpan)   // Use colored start time
                    .append(" | ")
                    .append(endTimeSpan)     // Use colored end time
                    .append("\n\n"); // Add extra newline for space between items
        }

        // Set the text in the TextView with the final styled text
        timeDisplay.setText(displayText);
    }


}
