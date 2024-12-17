package com.example.timerapp;

import android.app.AlertDialog;
import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import android.os.Environment;
import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private EditText textInput;
    private Button addButton;
    private RecyclerView recyclerView;
    private TextView timeDisplay;
    private ArrayList<String> items;
    private HashMap<String, TimeDetails> timers;
    private HashMap<String, Boolean> isRunning;
    private HashMap<String, Handler> handlers;
    private ItemAdapter itemAdapter;
    private ActivityResultLauncher<Intent> importFileLauncher;

    private boolean hasImportedFile = false;  // Flag to check if file was just imported

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        importFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri fileUri = result.getData().getData();
                        processImportedFile(fileUri);
                    }
                }
        );

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }

        textInput = findViewById(R.id.textInput);
        addButton = findViewById(R.id.addButton);
        recyclerView = findViewById(R.id.recyclerView);
        timeDisplay = findViewById(R.id.timeDisplay);

        Button saveButton = findViewById(R.id.saveButton);
        saveButton.setOnClickListener(v -> saveTimeDisplayToFile());

        Button clearDataButton = findViewById(R.id.clearDataButton);
        clearDataButton.setOnClickListener(v -> resetData());

        Button importButton = findViewById(R.id.importButton);
        importButton.setOnClickListener(v -> launchFilePicker());

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
                timers.put(text, new TimeDetails());
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

                if (timeDetails.getStartTime() == null) {
                    timeDetails.setStartTime(new Date());
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
                    handlers.get(itemName).postDelayed(this, 1000);
                }
            }
        };
        handlers.get(itemName).post(runnable);
    }

    public void deleteItem(String itemName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Kohteen poisto");
        builder.setMessage("Haluatko varmasti poistaa \"" + itemName + "\"?");

        builder.setPositiveButton("Kyllä", (dialog, which) -> {
            if (isRunning.get(itemName)) {
                handlers.get(itemName).removeCallbacksAndMessages(null); // Stop the Handler tasks
                isRunning.put(itemName, false); // Mark as stopped
            }

            items.remove(itemName);
            timers.remove(itemName);
            isRunning.remove(itemName);
            handlers.remove(itemName);

            itemAdapter.notifyDataSetChanged();
            updateTimeDisplay();

            Toast.makeText(this, "\"" + itemName + "\" deleted successfully.", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Ei", (dialog, which) -> {
            dialog.dismiss();
        });

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

                if (wasRunning) {
                    handlers.get(itemName).removeCallbacksAndMessages(null);
                    isRunning.put(itemName, true);
                }

                int index = items.indexOf(itemName);
                items.set(index, newName);

                TimeDetails details = timers.remove(itemName);
                timers.put(newName, details);

                Boolean runningState = isRunning.remove(itemName);
                isRunning.put(newName, runningState);

                Handler handler = handlers.remove(itemName);
                handlers.put(newName, handler);

                itemAdapter.notifyDataSetChanged();
                updateTimeDisplay();

                if (wasRunning) {
                    isRunning.put(newName, true);
                    startTimer(newName);
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

            // Extract start and end times as strings
            String startTime = (timeDetails.getStartTime() != null) ? dateFormat.format(timeDetails.getStartTime()) : "00:00";
            String endTime = (timeDetails.getEndTime() != null) ? dateFormat.format(timeDetails.getEndTime()) : "00:00";

            boolean isTaskRunning = isRunning.get(name); // Flag for running tasks

            // Calculate duration
            long durationMinutes = 0;
            double durationHours = 0.0;

            if (timeDetails.getStartTime() != null && timeDetails.getEndTime() != null) {
                long durationMillis = timeDetails.getEndTime().getTime() - timeDetails.getStartTime().getTime();
                durationMinutes = Math.round(durationMillis / (1000.0 * 60)); // Round to nearest minute

                if (durationMinutes == 0 && durationMillis > 0) {
                    durationMinutes = 1;
                }

                durationHours = Math.round((durationMinutes / 60.0) * 100.0) / 100.0; // Rounded to 2 decimals
            }

            // Start and End Times (Styled)
            SpannableString startEndTimes = new SpannableString("Aloitus: " + startTime + " | Lopetus: " + endTime);

            startEndTimes.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.startTimeColor)),
                    0, "Aloitus: ".length() + startTime.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            startEndTimes.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.endTimeColor)),
                    "Aloitus: ".length() + startTime.length() + 3,
                    startEndTimes.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            // Running Indicator (If Task is Running)
            SpannableString runningIndicator = new SpannableString(isTaskRunning ? " ⏳" : "");

            // Create duration string
            String durationText = "Kesto: " + durationMinutes + " minuuttia, " + String.format("%.2f", durationHours) + " tuntia";

            // Separator Line
            SpannableString separatorLine = new SpannableString("────────────────────");
            separatorLine.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.white)),
                    0, separatorLine.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            // Append all parts to display text
            displayText.append(name).append("\n")
                    .append(startEndTimes).append(runningIndicator).append("\n") // Ensures runningIndicator shows properly
                    .append(durationText).append("\n")
                    .append(separatorLine).append("\n");
        }

        timeDisplay.setText(displayText);
    }



    public void saveTimeDisplayToFile() {
        StringBuilder dataToSave = new StringBuilder();
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");

        for (String name : items) {
            TimeDetails timeDetails = timers.get(name);

            String startTime = (timeDetails.getStartTime() != null) ? dateFormat.format(timeDetails.getStartTime()) : "00:00";
            String endTime = (timeDetails.getEndTime() != null) ? dateFormat.format(timeDetails.getEndTime()) : "00:00";

            long durationMinutes = 0;
            double durationHours = 0.0;

            if (timeDetails.getStartTime() != null && timeDetails.getEndTime() != null) {
                long durationMillis = timeDetails.getEndTime().getTime() - timeDetails.getStartTime().getTime();
                durationMinutes = Math.round(durationMillis / (1000.0 * 60)); // Round to nearest minute

                if (durationMinutes == 0 && durationMillis > 0) {
                    durationMinutes = 1;
                }

                durationHours = Math.round((durationMinutes / 60.0) * 100.0) / 100.0; // Correctly rounded to 2 decimals
            }

            String runningIndicator = isRunning.get(name) ? " (Aika juoksee)" : "";

            dataToSave.append(name).append("\n")
                    .append("Aloitus: ").append(startTime).append(runningIndicator).append("\n")
                    .append("Lopetus: ").append(endTime).append("\n")
                    .append("Kesto: ").append(durationMinutes).append(" minuuttia, ")
                    .append(String.format("%.2f", durationHours)).append(" tuntia\n") // Ensure 2 decimal places
                    .append("--------------------").append("\n");
        }

        String fileName = "Lumityot " + new SimpleDateFormat("yyyy.MM.dd_HH.mm").format(new Date()) + ".txt";

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = getContentResolver();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri fileUri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues);
                if (fileUri != null) {
                    try (OutputStream outputStream = resolver.openOutputStream(fileUri)) {
                        outputStream.write(dataToSave.toString().getBytes());
                        Toast.makeText(this, "Tallennettu kansioon Lataukset", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File file = new File(directory, fileName);

                try (FileOutputStream outputStream = new FileOutputStream(file)) {
                    outputStream.write(dataToSave.toString().getBytes());
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
        hasImportedFile = false;
        super.onPause();

        SharedPreferences sharedPreferences = getSharedPreferences("TimerAppData", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putInt("items_count", items.size());
        for (int i = 0; i < items.size(); i++) {
            editor.putString("item_" + i, items.get(i));
        }

        for (String itemName : items) {
            TimeDetails timeDetails = timers.get(itemName);
            editor.putLong(itemName + "_startTime", timeDetails.getStartTime() != null ? timeDetails.getStartTime().getTime() : -1);
            editor.putLong(itemName + "_endTime", timeDetails.getEndTime() != null ? timeDetails.getEndTime().getTime() : -1);
            editor.putBoolean(itemName + "_isRunning", isRunning.get(itemName));
        }
        editor.apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        // Skip loading if a file has just been imported
        if (hasImportedFile) {
            Log.d("LoadData", "Skipping load: data was just imported.");
            return;
        }

        SharedPreferences sharedPreferences = getSharedPreferences("TimerAppData", MODE_PRIVATE);

        int itemCount = sharedPreferences.getInt("items_count", 0);
        items.clear();
        timers.clear();
        isRunning.clear();
        handlers.clear();

        for (int i = 0; i < itemCount; i++) {
            String itemName = sharedPreferences.getString("item_" + i, null);
            if (itemName != null) {
                items.add(itemName);
                timers.put(itemName, new TimeDetails());
                isRunning.put(itemName, sharedPreferences.getBoolean(itemName + "_isRunning", false));
                handlers.put(itemName, new Handler());

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

        itemAdapter.notifyDataSetChanged();
        updateTimeDisplay();
    }

    public void resetData() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Aloita alusta");
        builder.setMessage("Haluatko varmasti aloittaa alusta ja poistaa kaikki tiedot?");

        builder.setPositiveButton("Kyllä", (dialog, which) -> {
            stopAllTimers();

            clearAllData();

            itemAdapter.notifyDataSetChanged();

            Toast.makeText(this, "Kaikki tiedot poistettu ja sovellus aloitettu alusta.", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Ei", (dialog, which) -> {
            dialog.dismiss();
        });

        builder.show();
    }

    private void stopAllTimers() {
        for (String itemName : items) {
            if (isRunning.get(itemName)) {
                handlers.get(itemName).removeCallbacksAndMessages(null);
                isRunning.put(itemName, false);
            }
        }
    }

    public void clearAllData() {
        clearSharedPreferences();
        clearInternalData();
    }

    public void clearSharedPreferences() {
        SharedPreferences sharedPreferences = getSharedPreferences("TimerAppData", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.clear();
        editor.apply();

        Toast.makeText(this, "Data cleared!", Toast.LENGTH_SHORT).show();
    }

    public void clearInternalData() {
        items.clear();
        timers.clear();
        isRunning.clear();
        handlers.clear();

        updateTimeDisplay();

        Toast.makeText(this, "Internal data cleared!", Toast.LENGTH_SHORT).show();
    }

    private void launchFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        importFileLauncher.launch(intent);
    }

    private void processImportedFile(Uri fileUri) {
        try {
            items.clear();
            InputStream inputStream = getContentResolver().openInputStream(fileUri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            while ((line = reader.readLine()) != null) {
                String text = line.trim();
                if (!text.isEmpty() && !items.contains(text)) {
                    items.add(text);
                    timers.put(text, new TimeDetails());
                    isRunning.put(text, false);
                    handlers.put(text, new Handler());
                    updateTimeDisplay();
                }
            }

            // Notify adapter
            runOnUiThread(() -> {
                itemAdapter.notifyDataSetChanged();
            });

            // Mark the flag as "imported"
            hasImportedFile = true;

        } catch (Exception e) {
            Log.e("ImportError", "Error importing file", e);
        }
    }
}