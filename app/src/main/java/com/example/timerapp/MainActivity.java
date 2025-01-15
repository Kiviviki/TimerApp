package com.example.timerapp;

import android.app.AlertDialog;
import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import android.os.Environment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;

import java.io.File;
import java.io.IOException;
import java.util.List;

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

    private boolean hasImportedFile = false;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle drawerToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Initialize DrawerLayout and NavigationView
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);

        // Setup ActionBarDrawerToggle
        drawerToggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, // String resource for open description
                R.string.navigation_drawer_close // String resource for close description
        );
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState(); // Synchronize the state of the drawer toggle

        // Handle menu item clicks
        navigationView.setNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.menu_add) {
                addItem();
            } else if (itemId == R.id.menu_clear) {
                resetData();
            } else if (itemId == R.id.menu_save) {
                confirmSaveTimeDisplayToFile();
            } else if (itemId == R.id.menu_import) {
                launchFilePicker();
            } else if (itemId == R.id.menu_reset) {
                resetAllTimes();
            } else if (itemId == R.id.menu_load_internal) {
                showInternalFilePicker();
            } else if (itemId == R.id.menu_clear_internal_files) {
                clearInternalStorage();
            } else if (itemId == R.id.menu_load_flies) {
                showDefaultFilePicker();
            } else {
                return false;
            }

            drawerLayout.closeDrawers(); // Close the drawer
            return true;
        });


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

        recyclerView = findViewById(R.id.recyclerView);
        timeDisplay = findViewById(R.id.timeDisplay);
        ScrollView scrollView = findViewById(R.id.scrollView);

        View resizeHandle = findViewById(R.id.resizeHandle);

        resizeHandle.setOnTouchListener(new View.OnTouchListener() {
            private float lastY; // Tracks the last Y position
            private int initialHeight;
            private float lastHeight; // For smoothing
            private VelocityTracker velocityTracker;
            private float totalDeltaY = 0; // Tracks net motion
            private boolean isMovingUp; // Tracks the last movement direction

            private static final int COLLAPSED_HEIGHT = 0;
            private static final int EXPANDED_HEIGHT = 1800;

            private static final float SMOOTHING_FACTOR = 0.2f;
            private static final float FLING_DECELERATION = 0.6f;

            private boolean isFlinging = false;
            private float flingVelocity = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isFlinging = false;

                        lastY = event.getRawY();
                        initialHeight = scrollView.getLayoutParams().height;
                        lastHeight = initialHeight;
                        totalDeltaY = 0; // Reset the cumulative delta

                        isMovingUp = false; // Reset direction tracking

                        if (velocityTracker == null) {
                            velocityTracker = VelocityTracker.obtain();
                        } else {
                            velocityTracker.clear();
                        }
                        velocityTracker.addMovement(event);

                        // Change to the active resize color
                        resizeHandle.setBackgroundColor(
                                ContextCompat.getColor(MainActivity.this, R.color.activeResizeColor)
                        );
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        velocityTracker.addMovement(event);

                        float currentY = event.getRawY();
                        float deltaY = lastY - currentY; // Delta for this move event
                        totalDeltaY += deltaY; // Accumulate total motion

                        // Detect and handle direction change
                        boolean newDirection = (deltaY > 0) != isMovingUp;
                        if (newDirection) {
                            // Reset reference points when direction changes
                            isMovingUp = deltaY > 0;
                            totalDeltaY = 0; // Reset accumulated motion
                            lastY = currentY;
                            initialHeight = (int) lastHeight; // Adjust starting height for smooth motion
                        }

                        // Calculate target height
                        int targetHeight = (int) (initialHeight + totalDeltaY);
                        targetHeight = Math.max(COLLAPSED_HEIGHT, Math.min(EXPANDED_HEIGHT, targetHeight));

                        // Apply smoothing
                        lastHeight = lastHeight + (targetHeight - lastHeight) * SMOOTHING_FACTOR;
                        scrollView.getLayoutParams().height = (int) lastHeight;
                        scrollView.requestLayout();

                        lastY = currentY; // Update last position

                        // Change to the highlight color during a move
                        resizeHandle.setBackgroundColor(
                                ContextCompat.getColor(MainActivity.this, R.color.highlightMoveColor)
                        );
                        return true;

                    case MotionEvent.ACTION_UP:
                        resizeHandle.setBackgroundColor(
                                ContextCompat.getColor(MainActivity.this, R.color.defaultColor)
                        );

                        velocityTracker.addMovement(event);
                        velocityTracker.computeCurrentVelocity(1000);
                        flingVelocity = velocityTracker.getYVelocity();

                        // Use cumulative motion to determine final fling direction
                        if ((totalDeltaY > 0 && flingVelocity > 0) || (totalDeltaY < 0 && flingVelocity < 0)) {
                            flingVelocity = -flingVelocity; // Reverse velocity to align with motion
                        }

                        isFlinging = true;
                        startFling();

                        if (velocityTracker != null) {
                            velocityTracker.recycle();
                            velocityTracker = null;
                        }
                        return true;
                }
                return false;
            }

            private void startFling() {
                new Thread(() -> {
                    int currentHeight = scrollView.getLayoutParams().height;

                    while (isFlinging) {
                        try {
                            Thread.sleep(16); // 60 FPS
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        flingVelocity *= FLING_DECELERATION;

                        if (Math.abs(flingVelocity) < 1) {
                            isFlinging = false;
                            break;
                        }

                        int newHeight = (int) (currentHeight - flingVelocity * 0.1f);
                        if (newHeight <= COLLAPSED_HEIGHT) {
                            newHeight = COLLAPSED_HEIGHT;
                            isFlinging = false;
                        } else if (newHeight >= EXPANDED_HEIGHT) {
                            newHeight = EXPANDED_HEIGHT;
                            isFlinging = false;
                        }

                        final int finalHeight = newHeight;
                        scrollView.post(() -> {
                            scrollView.getLayoutParams().height = finalHeight;
                            scrollView.requestLayout();
                        });

                        currentHeight = newHeight;
                    }
                }).start();
            }
        });

        items = new ArrayList<>();
        timers = new HashMap<>();
        isRunning = new HashMap<>();
        handlers = new HashMap<>();

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        itemAdapter = new ItemAdapter(this, items, timers, isRunning, handlers);
        recyclerView.setAdapter(itemAdapter);
    }

    private void resetAllTimes() {
        if (timers != null && !timers.isEmpty()) {
            // Iterate over all items in the timers map
            for (String itemName : timers.keySet()) {
                // Reset TimeDetails for this timer
                TimeDetails timeDetails = timers.get(itemName);
                if (timeDetails != null) {
                    timeDetails.setStartTime(null);
                    timeDetails.setEndTime(null);
                }

                // Stop the running timer if any
                if (isRunning.containsKey(itemName) && isRunning.get(itemName)) {
                    isRunning.put(itemName, false);
                    if (handlers.containsKey(itemName)) {
                        handlers.get(itemName).removeCallbacksAndMessages(null);
                    }
                }
            }

            // Notify user or update UI
            Toast.makeText(this, "Kaikki ajat on nollattu.", Toast.LENGTH_SHORT).show();

            // Notify the adapter or refresh the UI to reflect the changes
            itemAdapter.notifyDataSetChanged();
            updateTimeDisplay();
        } else {
            Toast.makeText(this, "Aikoja ei löydy.", Toast.LENGTH_SHORT).show();
        }
    }

    private void addItem() {
        // Use MaterialAlertDialogBuilder for proper theming
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Lisää uusi kohde");

        final EditText input = new EditText(this);
        input.setHint("Anna kohteen nimi");

        // Style the EditText for better contrast in both themes
        int padding = getResources().getDimensionPixelSize(R.dimen.dialog_padding);
        input.setPadding(padding, padding, padding, padding);
        input.setBackgroundResource(R.drawable.edittext_background); // Optional: Add a custom background if needed.

        // Use a style-aware hint text color
        input.setHintTextColor(ContextCompat.getColor(this, R.color.hintTextColor));
        input.setTextColor(ContextCompat.getColor(this, R.color.textColorPrimary2));

        builder.setView(input);

        builder.setPositiveButton("Lisää", (dialog, which) -> {
            String text = input.getText().toString().trim();
            if (!text.isEmpty() && !items.contains(text)) {
                items.add(text);
                timers.put(text, new TimeDetails());
                isRunning.put(text, false);
                handlers.put(text, new Handler());

                itemAdapter.notifyDataSetChanged();
                updateTimeDisplay();
                Toast.makeText(this, "Kohde lisätty: " + text, Toast.LENGTH_SHORT).show();
            } else if (text.isEmpty()) {
                Toast.makeText(this, "Syötä jotain järkevää.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Tämänniminen kohde löytyy jo.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Hylkää", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    public void toggleTimer(String itemName) {
        if (isRunning.containsKey(itemName) && timers.containsKey(itemName)) {
            // Stop all other timers if they are running
            for (String key : isRunning.keySet()) {
                if (!key.equals(itemName) && isRunning.get(key)) {
                    // Stop the timer for this item
                    isRunning.put(key, false);
                    handlers.get(key).removeCallbacksAndMessages(null);
                    timers.get(key).setEndTime(new Date());
                    Toast.makeText(this, key + " pysäytetty.", Toast.LENGTH_SHORT).show();
                    itemAdapter.notifyDataSetChanged();
                }
            }

            int position = items.indexOf(itemName);
            if (position >= 0) {
                itemAdapter.notifyItemChanged(position);
            }

            // Now toggle the timer for the current item
            if (isRunning.get(itemName)) {
                // If the timer is running, stop it
                isRunning.put(itemName, false);
                handlers.get(itemName).removeCallbacksAndMessages(null);
                timers.get(itemName).setEndTime(new Date());
                Toast.makeText(this, itemName + " pysäytetty.", Toast.LENGTH_SHORT).show();
            } else {
                // If the timer is not running, start it
                isRunning.put(itemName, true);
                TimeDetails timeDetails = timers.get(itemName);

                if (timeDetails.getStartTime() == null) {
                    timeDetails.setStartTime(new Date());
                }

                startTimer(itemName);
                Toast.makeText(this, itemName + " aloitettu", Toast.LENGTH_SHORT).show();
            }

            // Update time display
            updateTimeDisplay();
        } else {
            Toast.makeText(this, "Virheellistä tietoa, tai tiedot on poistettu.", Toast.LENGTH_SHORT).show();
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
        builder.setTitle("Aikojen nollaus");
        builder.setMessage("Haluatko varmasti nollata kohteen \"" + itemName + "\" ajat?");

        builder.setPositiveButton("Kyllä", (dialog, which) -> {
            // Reset the TimeDetails for this item
            if (timers.containsKey(itemName)) {
                TimeDetails timeDetails = timers.get(itemName);
                if (timeDetails != null) {
                    timeDetails.setStartTime(null);
                    timeDetails.setEndTime(null);
                }
            }

            // Stop the running timer if it exists
            if (isRunning.containsKey(itemName) && isRunning.get(itemName)) {
                isRunning.put(itemName, false);
                if (handlers.containsKey(itemName)) {
                    handlers.get(itemName).removeCallbacksAndMessages(null);
                }
            }

            // Notify the adapter or refresh the UI to reflect the changes
            itemAdapter.notifyDataSetChanged();
            updateTimeDisplay();

            Toast.makeText(this, "Ajat kohteelle \"" + itemName + "\" on nollattu.", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Ei", (dialog, which) -> {
            dialog.dismiss();
        });

        builder.show();
    }


    public void editItem(String itemName) {
        // Use MaterialAlertDialogBuilder for proper theming
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Muokkaa kohteen nimeä");

        final EditText input = new EditText(this);
        input.setText(itemName);

        // Style the EditText for better contrast in both themes
        int padding = getResources().getDimensionPixelSize(R.dimen.dialog_padding);
        input.setPadding(padding, padding, padding, padding);
        input.setBackgroundResource(R.drawable.edittext_background);
        input.setHint("Anna uusi nimi");
        input.setHintTextColor(ContextCompat.getColor(this, R.color.hintTextColor));
        input.setTextColor(ContextCompat.getColor(this, R.color.textColorPrimary2));

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
                Toast.makeText(this, "Tämän niminen kohde löytyy jo.", Toast.LENGTH_SHORT).show();
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

            String startTime = (timeDetails.getStartTime() != null) ? dateFormat.format(timeDetails.getStartTime()) : "00:00";
            String endTime = (timeDetails.getEndTime() != null) ? dateFormat.format(timeDetails.getEndTime()) : "00:00";

            boolean isTaskRunning = isRunning.get(name);

            long durationMinutes = 0;
            double durationHours = 0.0;

            if (timeDetails.getStartTime() != null && timeDetails.getEndTime() != null) {
                long durationMillis = timeDetails.getEndTime().getTime() - timeDetails.getStartTime().getTime();
                durationMinutes = Math.round(durationMillis / (1000.0 * 60));

                if (durationMinutes == 0 && durationMillis > 0) {
                    durationMinutes = 1;
                }

                durationHours = Math.round((durationMinutes / 60.0) * 100.0) / 100.0;
            }

            SpannableString startEndTimes = new SpannableString("Aloitus: " + startTime + " | Lopetus: " + endTime);
            startEndTimes.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.startTimeColor)),
                    0, "Aloitus: ".length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            startEndTimes.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.startTimeBgColor)),
                    "Aloitus: ".length(), "Aloitus: ".length() + startTime.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            startEndTimes.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.endTimeColor)),
                    "Aloitus: ".length() + startTime.length() + 3, "Aloitus: ".length() + startTime.length() + 3 + "| Lopetus: ".length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            startEndTimes.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.endTimeBgColor)),
                    "Aloitus: ".length() + startTime.length() + "| Lopetus: ".length(), startEndTimes.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            SpannableString runningIndicator = new SpannableString(isTaskRunning ? " ⏳" : "");

            String durationText = "Kesto: " + durationMinutes + " minuuttia, " + String.format("%.2f", durationHours) + " tuntia";
            SpannableString durationSpannable = new SpannableString(durationText);
            durationSpannable.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.kestoColor)),
                    0, "Kesto: ".length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            int minutesStart = durationText.indexOf("minuuttia");
            durationSpannable.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.kestoColor)),
                    minutesStart, minutesStart + "minuuttia".length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            int hoursStart = durationText.indexOf("tuntia");
            durationSpannable.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.kestoColor)),
                    hoursStart, hoursStart + "tuntia".length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            String minutesValue = String.valueOf(durationMinutes);
            int minutesValueStart = durationText.indexOf(minutesValue);
            int minutesValueEnd = minutesValueStart + minutesValue.length();
            durationSpannable.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.minutesColor)), // Custom color for minutes
                    minutesValueStart, minutesValueEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            String hoursValue = String.format("%.2f", durationHours);
            int hoursValueStart = durationText.indexOf(hoursValue);
            int hoursValueEnd = hoursValueStart + hoursValue.length();
            durationSpannable.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.hoursColor)), // Custom color for hours
                    hoursValueStart, hoursValueEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            SpannableString separatorLine = new SpannableString("────────────────────");
            separatorLine.setSpan(
                    new ForegroundColorSpan(ContextCompat.getColor(this, R.color.white)),
                    0, separatorLine.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );

            SpannableString nameSpannable = new SpannableString(name);
            nameSpannable.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    String textToCopy = name.length() > 6 ? name.substring(0, 6) : name;
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Copied Text", textToCopy);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(widget.getContext(), "Copied: " + textToCopy, Toast.LENGTH_SHORT).show();
                }
            }, 0, name.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            displayText.append(nameSpannable).append("\n")
                    .append(startEndTimes).append(runningIndicator).append("\n")
                    .append(durationSpannable).append("\n")
                    .append(separatorLine).append("\n");
        }

        timeDisplay.setText(displayText);
        timeDisplay.setMovementMethod(LinkMovementMethod.getInstance()); // Enable clickable spans
    }

    public void confirmSaveTimeDisplayToFile() {
        // Prepare the alert dialog for confirmation
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Haluatko tallentaa tiedot?")
                .setCancelable(true)  // Allows dialog to be dismissed when touching outside
                .setPositiveButton("Kyllä", (dialog, id) -> {
                    // User confirmed, now save the file
                    saveTimeDisplayToFile();
                })
                .setNegativeButton("Ei", (dialog, id) -> {
                    // User declined, just dismiss the dialog
                    dialog.dismiss();
                });

        // Create and show the dialog
        AlertDialog alert = builder.create();
        alert.show();
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
                durationMinutes = Math.round(durationMillis / (1000.0 * 60));

                if (durationMinutes == 0 && durationMillis > 0) {
                    durationMinutes = 1;
                }

                durationHours = Math.round((durationMinutes / 60.0) * 100.0) / 100.0;
            }
            String runningIndicator = isRunning.get(name) ? " (Aika juoksee)" : "";

            dataToSave.append(name).append("\n")
                    .append("Aloitus: ").append(startTime).append(runningIndicator).append("\n")
                    .append("Lopetus: ").append(endTime).append("\n")
                    .append("Kesto: ").append(durationMinutes).append(" minuuttia, ")
                    .append(String.format("%.2f", durationHours)).append(" tuntia\n")
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
        builder.setMessage("Haluatko varmasti poistaa kaikki tiedot ja aloittaa alusta?");

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

    }

    public void clearInternalData() {
        items.clear();
        timers.clear();
        isRunning.clear();
        handlers.clear();

        updateTimeDisplay();

    }

    private void launchFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        importFileLauncher.launch(intent);
    }

    private void processImportedFile(Uri fileUri) {
        try {
            // Extract filename from the URI
            String fileName = getFileNameFromUri(fileUri);

            // Save the file to internal storage
            saveFileToInternalStorage(fileUri, fileName);

            // Load content into the app's memory
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
                }
            }

            inputStream.close();
            runOnUiThread(() -> itemAdapter.notifyDataSetChanged());
            hasImportedFile = true;
            updateTimeDisplay();

        } catch (Exception e) {
            Log.e("ImportError", "Virhe tiedostoa tuodessa!", e);
        }
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;

        // Check if the scheme is 'content'
        if (uri.getScheme().equals("content")) {
            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) { // Ensure valid index
                        result = cursor.getString(nameIndex);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        // If result is still null, fallback to the file path
        if (result == null) {
            String path = uri.getPath();
            int cut = (path != null) ? path.lastIndexOf('/') : -1;
            if (cut != -1 && path != null) {
                result = path.substring(cut + 1);
            }
        }

        return result;
    }

    private void saveFileToInternalStorage(Uri fileUri, String fileName) {
        try (InputStream inputStream = getContentResolver().openInputStream(fileUri);
             FileOutputStream outputStream = openFileOutput(fileName, MODE_PRIVATE)) {

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
        } catch (IOException e) {
            Log.e("SaveFileError", "Virhe tiedostoa tallentaessa sisäiseen tallennukseen", e);
        }
    }

    private void showInternalFilePicker() {
        File internalDir = getFilesDir();
        File[] files = internalDir.listFiles(); // List files in internal storage

        if (files == null || files.length == 0) {
            Toast.makeText(this, "Tuo listoja kohdasta 'Tuo kohteet'", Toast.LENGTH_SHORT).show();
            return;
        }

        // Initialize a list to hold file names excluding 'profileInstalled'
        List<String> validFileNames = new ArrayList<>();

        for (File file : files) {
            // Check if the file is not 'profileInstalled' and add it to the list
            if (!file.getName().equals("profileInstalled")) {
                validFileNames.add(file.getName());
            }
        }

        // If no valid files are left after excluding 'profileInstalled'
        if (validFileNames.isEmpty()) {
            Toast.makeText(this, "Ei löytynyt tiedostoja!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Convert the list of valid file names to an array
        String[] fileNames = validFileNames.toArray(new String[0]);

        // Show dialog with valid files
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Valitse tiedosto")
                .setItems(fileNames, (dialog, which) -> {
                    loadInternalFile(fileNames[which]);
                })
                .show();
    }

    private void loadInternalFile(String fileName) {
        try {
            FileInputStream inputStream = openFileInput(fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            items.clear();
            String line;
            while ((line = reader.readLine()) != null) {
                String text = line.trim();
                if (!text.isEmpty() && !items.contains(text)) {
                    items.add(text);
                    timers.put(text, new TimeDetails());
                    isRunning.put(text, false);
                    handlers.put(text, new Handler());
                }
            }
            inputStream.close();

            runOnUiThread(() -> itemAdapter.notifyDataSetChanged());
            hasImportedFile = true;

        } catch (IOException e) {
            Log.e("LoadFileError", "Virhe ladatessa sisäistä tiedostoa: " + fileName, e);
        }
    }

    private void clearInternalStorage() {
        File internalDir = getFilesDir();
        File[] files = internalDir.listFiles(); // List all files in the directory

        if (files != null && files.length > 0) {
            for (File file : files) {
                if (file.isFile()) {
                    boolean deleted = file.delete(); // Attempt to delete each file
                    if (!deleted) {
                        Log.e("FileDeleteError", "Tiedostoa ei voitu poistaa: " + file.getName());
                    }
                }
            }
            Toast.makeText(this, "Kaikki sisäiset tiedostot poistettu.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Ei sisäisiä tiedostoja poistettavaksi.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDefaultFilePicker() {
        String[] predefinedFiles = {"auraukset.txt", "kolaukset.txt"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Valitse tiedosto")
                .setItems(predefinedFiles, (dialog, which) -> {
                    loadDefaultFile(predefinedFiles[which]);
                })
                .show();
    }

    private void loadDefaultFile(String fileName) {
        try {
            InputStream inputStream = getAssets().open(fileName); // If stored in assets
            // InputStream inputStream = getResources().openRawResource(R.raw.sample1); // If stored in res/raw

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            items.clear();

            while ((line = reader.readLine()) != null) {
                String text = line.trim();
                if (!text.isEmpty() && !items.contains(text)) {
                    items.add(text);
                    timers.put(text, new TimeDetails());
                    isRunning.put(text, false);
                    handlers.put(text, new Handler());
                }
            }
            inputStream.close();
            runOnUiThread(() -> itemAdapter.notifyDataSetChanged());

            hasImportedFile = true;
            updateTimeDisplay();

        } catch (IOException e) {
            Log.e("FileLoadError", "Virhe ladatessa sisäistä tiedostoa!", e);
        }
    }
}