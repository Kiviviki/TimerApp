package com.example.timerapp;

import android.app.AlertDialog;
import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler; // Correct import
import android.provider.MediaStore;
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

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            // For API level 23 and higher
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        } else {
            // For API level 21-22, permissions are granted at install time
            // No action needed
        }

        textInput = findViewById(R.id.textInput);
        addButton = findViewById(R.id.addButton);
        recyclerView = findViewById(R.id.recyclerView);
        timeDisplay = findViewById(R.id.timeDisplay);

        Button saveButton = findViewById(R.id.saveButton);
        saveButton.setOnClickListener(v -> saveTimeDisplayToFile());

        Button clearDataButton = findViewById(R.id.clearDataButton);
        clearDataButton.setOnClickListener(v -> resetData());

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
        if (isRunning.containsKey(itemName) && timers.containsKey(itemName)) {
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
        } else {
            Toast.makeText(this, "Invalid item or data has been cleared.", Toast.LENGTH_SHORT).show();
        }
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

    // Function to save timeDisplay content to a file
    public void saveTimeDisplayToFile() {
        String dataToSave = timeDisplay.getText().toString();
        String fileName = "Lumityot " + new SimpleDateFormat("yyyy.MM.dd_HH.mm").format(new Date()) + ".txt";

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ContentResolver resolver = getContentResolver();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri fileUri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues);
                if (fileUri != null) {
                    try (OutputStream outputStream = resolver.openOutputStream(fileUri)) {
                        outputStream.write(dataToSave.getBytes());
                        Toast.makeText(this, "Tallennettu kansioon Lataukset", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File file = new File(directory, fileName);

                try (FileOutputStream outputStream = new FileOutputStream(file)) {
                    outputStream.write(dataToSave.getBytes());
                    Toast.makeText(this, "Tallennettu kansioon Lataukset", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Tietoja ei voitu tallentaa: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Get the SharedPreferences editor to save data
        SharedPreferences sharedPreferences = getSharedPreferences("TimerAppData", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        // Save items list to SharedPreferences
        editor.putInt("items_count", items.size());
        for (int i = 0; i < items.size(); i++) {
            editor.putString("item_" + i, items.get(i));
        }

        // Save timer data (for example, you could save the start and end times of each timer)
        for (String itemName : items) {
            TimeDetails timeDetails = timers.get(itemName);
            editor.putLong(itemName + "_startTime", timeDetails.getStartTime() != null ? timeDetails.getStartTime().getTime() : -1);
            editor.putLong(itemName + "_endTime", timeDetails.getEndTime() != null ? timeDetails.getEndTime().getTime() : -1);
            editor.putBoolean(itemName + "_isRunning", isRunning.get(itemName));  // Save running state
        }

        // Commit changes to SharedPreferences
        editor.apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        SharedPreferences sharedPreferences = getSharedPreferences("TimerAppData", MODE_PRIVATE);

        // Load items list from SharedPreferences
        int itemCount = sharedPreferences.getInt("items_count", 0);
        items.clear();
        timers.clear();
        isRunning.clear();
        handlers.clear(); // Ensure handlers are cleared before reloading

        for (int i = 0; i < itemCount; i++) {
            String itemName = sharedPreferences.getString("item_" + i, null);
            if (itemName != null) {
                items.add(itemName);
                timers.put(itemName, new TimeDetails()); // Initialize new TimeDetails object for each item
                isRunning.put(itemName, sharedPreferences.getBoolean(itemName + "_isRunning", false));
                handlers.put(itemName, new Handler()); // Reinitialize handler for each item

                // Retrieve and set start and end times for each item
                long startTimeMillis = sharedPreferences.getLong(itemName + "_startTime", -1);
                long endTimeMillis = sharedPreferences.getLong(itemName + "_endTime", -1);

                if (startTimeMillis != -1) {
                    timers.get(itemName).setStartTime(new Date(startTimeMillis));
                }

                if (endTimeMillis != -1) {
                    timers.get(itemName).setEndTime(new Date(endTimeMillis));
                }
            }
        }

        // Notify the adapter to update the UI
        itemAdapter.notifyDataSetChanged();
        updateTimeDisplay();
    }


    public void resetData() {
        // Show confirmation dialog before clearing all data
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Aloita alusta");
        builder.setMessage("Haluatko varmasti aloittaa alusta ja poistaa kaikki tiedot?");

        // "Yes" Button
        builder.setPositiveButton("Kyllä", (dialog, which) -> {
            // Stop any running timers and clear handlers
            stopAllTimers();

            // Clear all data (internal data and SharedPreferences)
            clearAllData();

            // Notify the adapter after the data is cleared
            itemAdapter.notifyDataSetChanged();  // Notify the adapter after clearing data

            // Show success message
            Toast.makeText(this, "Kaikki tiedot poistettu ja sovellus aloitettu alusta.", Toast.LENGTH_SHORT).show();
        });

        // "No" Button
        builder.setNegativeButton("Ei", (dialog, which) -> {
            // Simply dismiss the dialog
            dialog.dismiss();
        });

        // Show the dialog
        builder.show();
    }

    private void stopAllTimers() {
        // Stop all timers and remove handlers
        for (String itemName : items) {
            if (isRunning.get(itemName)) {
                handlers.get(itemName).removeCallbacksAndMessages(null); // Stop the Handler tasks
                isRunning.put(itemName, false); // Mark as stopped
            }
        }
    }

    public void clearAllData() {
        // Clear SharedPreferences
        clearSharedPreferences();

        // Reset internal data
        clearInternalData();
    }

    public void clearSharedPreferences() {
        SharedPreferences sharedPreferences = getSharedPreferences("TimerAppData", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        // Clear all stored data in SharedPreferences
        editor.clear();
        editor.apply();

        Toast.makeText(this, "Data cleared!", Toast.LENGTH_SHORT).show();
    }

    public void clearInternalData() {
        // Clear the list and maps
        items.clear();
        timers.clear();
        isRunning.clear();
        handlers.clear();

        // Reset the time display (clear the time display area)
        updateTimeDisplay();

        // If necessary, notify the adapter to update the UI
        // itemAdapter.notifyDataSetChanged();

        Toast.makeText(this, "Internal data cleared!", Toast.LENGTH_SHORT).show();
    }

}
