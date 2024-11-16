package com.example.assingment3backup;

import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.NotificationManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.AsyncListUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.PrimaryKey;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;


import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private NumberPicker hoursPicker, minutesPicker, secondsPicker;
    private boolean countDown = false;
    private ViewGroup mainLayout;
    private View inflatedLayout, inflatedLayoutStart;
    private timeMetrics metrics;
    private NotificationManager notificationManager;
    private RecyclerView recyclerView;
    private List<AppUsageInfo> appUsageInfoList;
    private MyAdapter adapter;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dataCollectionSchedule();
        EdgeToEdge.enable(this);
        UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        metrics = null;

        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS); // Checks if we have the permission to check usages stats
        if (permissionGranted()) {
            long[] time = retrieveData(usageStatsManager); // Collects the main data
            metrics = new timeMetrics(time[0], time[1], time[2], time[3], time[4], retrieveDataDays(usageStatsManager), retrieveDataMonths(usageStatsManager));

        } else {
            startActivity(intent);
        }
        if (!notificationManager.isNotificationPolicyAccessGranted()) { // Checks if we have permission to change the notification policy
            Intent intent2 = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            startActivity(intent2);
        }


        setContentView(R.layout.books_read);

        startingMenu(metrics);
    }

    private void countDown(int hours, int minutes, int seconds) { // This is the count down for the app
        countDown = true;
        mainLayout = findViewById(R.id.main);
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflatedLayout = inflater.inflate(R.layout.countdown_screeen, null); // creates an inflated layout to swap out the current view

        mainLayout.removeAllViews();
        mainLayout.addView(inflatedLayout);

        ProgressBar progressBar = findViewById(R.id.progressBar);

        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);


        WindowInsetsController controller = getWindow().getInsetsController();
        controller.hide(WindowInsets.Type.statusBars());
        controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);


        int totalTimeMilliseconds = (hours * 3600 + minutes * 60 + seconds) * 1000; // Gets the total time the user has selected
        TextView textView = inflatedLayout.findViewById(R.id.time_display);

        Button give_up = findViewById(R.id.give_up);

        new CountDownTimer(totalTimeMilliseconds, 1000) { // Starts the countdown ticks
            @Override
            public void onTick(long l) {
                long hours = l / (1000 * 3600);
                long minutes = (l % (3600 * 1000)) / (60 * 1000);
                long seconds = (l % (60 * 1000))/ 1000;

                int progressPercent = (int) (100 * l / totalTimeMilliseconds); // Moves the preogress bar
                progressBar.setProgress(progressPercent);

                String time = String.format("%02d:%02d:%02d", hours, minutes, seconds); // Displays the countdown
                textView.setText(time);

            }

            @Override
            public void onFinish() { // When the countdown finishes we swap back to the main menu
                progressBar.setProgress(0);
                countDown = false;
                mainLayout.removeView(inflatedLayout);
                setContentView(R.layout.menu);
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
                setValues();
            }
        }.start();

        give_up.setOnClickListener(v -> { // When the give up button is pressed a dialog is triggered
            dialog_build();
        });

        OnBackPressedCallback callback = new OnBackPressedCallback(true) { // If you try to use the phones back button a dialong is triggered
            @Override
            public void handleOnBackPressed() {
                dialog_build();
            }
        };

        getOnBackPressedDispatcher().addCallback(this, callback);

    }

    @Override
    protected void onStop() { // If you leave the app the timer ends
        super.onStop();
        if (countDown) {
            countDown = false;
            mainLayout.removeView(inflatedLayout);
            setContentView(R.layout.menu);
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
            setValues();
            dialog_buildFail();
        }
    }



    private void dialog_build() { // This is the dialong for when you attempt to back or just give up
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Giving up?").setMessage("Are you Sure you want to give up?")
                .setPositiveButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        }).setNegativeButton("Give up", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                countDown = false;
                dialogInterface.dismiss();
                mainLayout.removeView(inflatedLayout);
                setContentView(R.layout.menu);
                setValues();
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void dialog_buildFail() { // This is the dialog for when you leave the app
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Giving up?").setMessage("You left the app your timer has Ended")
                .setNegativeButton("Giving up", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        countDown = false;
                        dialogInterface.dismiss();
                        mainLayout.removeView(inflatedLayout);
                        setContentView(R.layout.menu);
                        setValues();
                    }
                });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void startingMenu(timeMetrics metrics) { // This is what is dispalyed at start up, it is supposed to show an idea of how muc time youre spending

        ScrollView scrollView = findViewById(R.id.scrollView);
        TextView bookData, bookInfo, skillData, skillInfo,
                degreeData, degreeInfo, langData, langInfo,
                instruData, instruInfo, totalTime;
        ImageView book, skill, degree, lang, instrument;
        Button button = findViewById(R.id.Next);

        TextView[] textViews = new TextView[11];
        ImageView[] imageViews = new ImageView[5];

        bookData = findViewById(R.id.book_data);
        bookInfo = findViewById(R.id.bookinfo);
        skillData = findViewById(R.id.skill_data);
        skillInfo = findViewById(R.id.skilInfo);
        degreeData = findViewById(R.id.degree_data);
        degreeInfo = findViewById(R.id.degreeinfo);
        langData = findViewById(R.id.language_data);
        langInfo = findViewById(R.id.languageinfo);
        instruData = findViewById(R.id.instrument_data);
        instruInfo = findViewById(R.id.instrumentinfo);
        totalTime = findViewById(R.id.totalTime);

        bookData.setText(String.valueOf(metrics.booksRead() + " Books"));
        skillData.setText(String.valueOf(metrics.skillsLearnt() + " Skills"));
        degreeData.setText(String.valueOf(metrics.degreesLearnt() + " Degrees"));
        langData.setText(String.valueOf(metrics.languagesLearnt() + " Languages"));
        instruData.setText(String.valueOf(metrics.instrumentLearnt() + " Instruments"));
        totalTime.setText("You have spent a total of " + String.valueOf(metrics.getTimeSpentTotal() + " hours on your phone over the past 2 weeks"));


        textViews[0] = bookData;
        textViews[1] = bookInfo;
        textViews[2] = skillData;
        textViews[3] = skillInfo;
        textViews[4] = degreeData;
        textViews[5] = degreeInfo;
        textViews[6] = langData;
        textViews[7] = langInfo;
        textViews[8] = instruData;
        textViews[9] = instruInfo;
        textViews[10] = totalTime;

        book = findViewById(R.id.book);
        skill = findViewById(R.id.skill);
        degree = findViewById(R.id.degree);
        lang = findViewById(R.id.language);
        instrument = findViewById(R.id.instrument);

        imageViews[0] = book;
        imageViews[1] = skill;
        imageViews[2] = degree;
        imageViews[3] = lang;
        imageViews[4] = instrument;

        for (TextView textView : textViews) {
            textView.setTranslationX(-1500);
        }

        for (ImageView imageView : imageViews) {
            imageView.setTranslationX(-1500);
        }

        scrollView.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() { // Animations for when you scroll
            @Override
            public void onScrollChanged() {
                int scrollY = scrollView.getScrollY();

                for (TextView textView : textViews) {
                    if (scrollY >= textView.getTop() - scrollView.getHeight() + 75) {
                        textView.animate().translationX(0).setDuration(175);
                    }
                }

                for (ImageView imageView : imageViews) {
                    if (scrollY >= imageView.getTop() - scrollView.getHeight() + 75) {
                        imageView.animate().translationX(0).setDuration(175);
                    }
                }
            }
        });

        button.setOnClickListener(new View.OnClickListener() { // Takes you to the main menu
            @Override
            public void onClick(View view) {
                setContentView(R.layout.menu);
                setValues();
            }
        });


    }

    private void statistics(timeMetrics metrics) { // This displays your usage stats on grpahs
        mainLayout = findViewById(R.id.main);
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflatedLayout = inflater.inflate(R.layout.statistics_screen, null);

        mainLayout.removeAllViews();
        mainLayout.addView(inflatedLayout);

        CombinedChart combinedChart = findViewById(R.id.combinedChart);
        CombinedData combinedData = new CombinedData();

        String[] days = new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        String[] months = new String[]{"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

        long[] daysData = metrics.getTimeSpentDays();
        long[] monthsData = metrics.getTimeSpentMonths();

        String axisInfoDays = "Daily Usage";
        String axisInfoMonths = "Monthly Usage";

        Button prev = findViewById(R.id.prev);
        Button next = findViewById(R.id.nextSlide);
        Button back = findViewById(R.id.exit);

        usage(daysData, combinedData, combinedChart, days, axisInfoDays); // This initialises the data for a weeks worth of usage

        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                usage(daysData, combinedData, combinedChart, days, axisInfoDays);
            }
        });

        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                usage(monthsData, combinedData, combinedChart, months, axisInfoMonths);
            }
        });

        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mainLayout.removeView(inflatedLayout);
                setContentView(R.layout.menu);
                setValues();
            }
        });

    }


    // This creates a bar graph combined with a line graph
    private void usage(long[] daysOrMonth, CombinedData combinedData, CombinedChart combinedChart, String[] daysOrMonths, String axisInfo) {
        List<Entry> entries = new ArrayList<>();
        List<BarEntry> barEntries = new ArrayList<>();

        if (daysOrMonth.length == 7) {
            Calendar calendar = Calendar.getInstance();
            int currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1;

            for (int indx = 0; indx < currentDayOfWeek; indx++) {
                float hours = (float) (daysOrMonth[indx] / (1000 * 3600));
                entries.add(new Entry(indx, hours));
                barEntries.add(new BarEntry(indx, hours));
            }
        } else {
            for (int indx = 0; indx < daysOrMonth.length; indx++) {
                float hours = (float) (daysOrMonth[indx] / (1000 * 3600));
                entries.add(new Entry(indx, hours));
                barEntries.add(new BarEntry(indx, hours));
            }
        }


        LineDataSet lineDataSet = new LineDataSet(entries, axisInfo);
        lineDataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        LineData lineData = new LineData(lineDataSet);

        BarDataSet barDataSet = new BarDataSet(barEntries, axisInfo);
        barDataSet.setColors(ColorTemplate.LIBERTY_COLORS);
        BarData barData = new BarData(barDataSet);

        combinedData.setData(lineData); // Combines the data from the line graph and bar graph
        combinedData.setData(barData);
        combinedChart.setData(combinedData);

        combinedChart.getDescription().setEnabled(true);
        Description description = new Description();
        description.setText(axisInfo);
        description.setTextSize(16f);
        description.setPosition(900f, 85f);
        combinedChart.setDescription(description);
        combinedChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTH_SIDED);
        combinedChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(daysOrMonths));
        combinedChart.animateX(1000);

        YAxis yAxis = combinedChart.getAxisLeft();
        yAxis.setAxisMinimum(0f);
        yAxis.setValueFormatter(new ValueFormatter() { // Formats the graph to disaply data type on y axis
            @Override
            public String getFormattedValue(float value) {
                return value + " hours";
            }
        });

        combinedChart.invalidate();
    }


    private void setValues() { // This is the start up menu
        hoursPicker = findViewById(R.id.NumberPickerHours);
        minutesPicker = findViewById(R.id.NumberPickerMinutes);
        secondsPicker = findViewById(R.id.NumberPickerSeconds);

        hoursPicker.setMinValue(0);
        minutesPicker.setMinValue(0);
        secondsPicker.setMinValue(0);

        hoursPicker.setMaxValue(23);
        minutesPicker.setMaxValue(59);
        secondsPicker.setMaxValue(59);

        TextView stat = findViewById(R.id.usage_stats);
        Button next = findViewById(R.id.next_stat);
        Button apps = findViewById(R.id.apps);

        String[] stats = new String[5];
        stats[0] = "Daily usage: " + String.valueOf(metrics.getTimeSpentDay() + " hours");
        stats[1] = "Weekly usage: " + String.valueOf(metrics.getTimeSpentWeek() + " hours");
        stats[2] = "Monthly usage: " + String.valueOf(metrics.getTimeSpentMonth() + " hours");
        stats[3] = "Total usage: " + String.valueOf(metrics.getTimeSpentTotal() + " hours");


        stat.setText(stats[0]);

        Button focus = findViewById(R.id.focus_button); // This will lead to the countdown menu and checks if youve inputted a valid number
        focus.setOnClickListener(v -> {
            if (hoursPicker.getValue()== 0 && minutesPicker.getValue()== 0 && secondsPicker.getValue()== 0) {
                Toast.makeText(getApplicationContext(), "Please input a valid time", Toast.LENGTH_SHORT).show();
            } else {
                countDown(hoursPicker.getValue(), minutesPicker.getValue(), secondsPicker.getValue());
            }
        });

        Button statistics = findViewById(R.id.stats);
        statistics.setOnClickListener(new View.OnClickListener() { // This takes you to the statistics menu
            @Override
            public void onClick(View view) {
                statistics(metrics);
            }
        });

        next.setOnClickListener(new View.OnClickListener() { // This displays some basic info about usage
            int count = 0;
            @Override
            public void onClick(View view) {
                count++;
                if (count > 3) {
                    count = 0;
                }
                stat.setText(stats[count]);
            }
        });

        apps.setOnClickListener(new View.OnClickListener() { // This shows a list of apps the times youve spent on them
            @Override
            public void onClick(View view) {
                showRecyclerLayout();
            }
        });
    }

    private void showRecyclerLayout() { // This sets up the recycler layout to display the apps that you have used
        setContentView(R.layout.app_list);
        Toast.makeText(getApplicationContext(), "Time collected form the past week", Toast.LENGTH_LONG).show();
        recyclerView = findViewById(R.id.app_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        Context context = this;


        getApps(this, new getAppsCallback() { // This puts the process of getting app data on a background thread
            @Override
            public void onAppsRetrieved(List<AppUsageInfo> appInfo) {
                appUsageInfoList = appInfo;
                adapter = new MyAdapter(appUsageInfoList, context);
                recyclerView.setAdapter(adapter);
            }
        });

        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                setContentView(R.layout.menu);
                setValues();
            }
        };

        getOnBackPressedDispatcher().addCallback(this, callback);


    }


    private boolean permissionGranted() { // This is used to check if permission has been granted for getting usage stats
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private long[] retrieveData(UsageStatsManager usageStatsManager) { // This retrieves usage data

        long[] totalTimes = new long[5];

        Calendar calendar = Calendar.getInstance();
        long currentTime = System.currentTimeMillis();

        calendar.set(Calendar.DAY_OF_YEAR, calendar.get(Calendar.DAY_OF_YEAR) - 1); // This retrieves data for a day
        long startOfDay = calendar.getTimeInMillis();
        totalTimes[0] = getTotalTime(usageStatsManager, startOfDay, currentTime);

        calendar.set(Calendar.DAY_OF_YEAR, calendar.get(Calendar.DAY_OF_YEAR) - 7); // This retrieves data for a week
        long startOfWeek = calendar.getTimeInMillis();
        totalTimes[1] = getTotalTime(usageStatsManager, startOfWeek, currentTime);

        calendar.set(Calendar.MONTH, calendar.get(Calendar.MONTH) - 1); // This retrieves data for a month
        long startOfMonth = calendar.getTimeInMillis();
        totalTimes[2] = getTotalTime(usageStatsManager, startOfMonth, currentTime);

        calendar.set(Calendar.YEAR, calendar.get(Calendar.YEAR) - 1); // This retrieves data for a year
        long startOfYear = calendar.getTimeInMillis();
        totalTimes[3] = getTotalTime(usageStatsManager, startOfYear, currentTime);

        totalTimes[4] = getTotalTime(usageStatsManager, 0 , currentTime); // This retrieves total data

        dataBaseTotal(currentTime, databaseUsage -> { // This gets data from the database on a background thread
            totalTimes[4] += databaseUsage;
        });

        return totalTimes;

    }

    private void dataBaseTotal(long currentTime, TotalDataCallback callback) { // This creates a background thread to collect data from the database
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        executorService.execute(() -> {
            AppDatabase database = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "usage_db").build();
            long databaseUsage = database.usageDao().getTotalUsageBetweenDates(0, currentTime);

            // Return data through the callback
            runOnUiThread(() -> {
                callback.onDataRetrieved(databaseUsage);
            });
        });
    }

    public interface TotalDataCallback {
        void onDataRetrieved(long databaseUsage);
    }



    private long[] retrieveDataDays(UsageStatsManager usageStatsManager) { // This creates a array of usage times for each day of the week
        long[] totalTimeDays = new long[7];

        Calendar calendar = Calendar.getInstance();
        int currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1;

        for (int indx = 0; indx <= currentDayOfWeek; indx++) {
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long startOfDay = calendar.getTimeInMillis();

            calendar.set(Calendar.HOUR_OF_DAY, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            calendar.set(Calendar.MILLISECOND, 999);
            long endOfDay = calendar.getTimeInMillis();

            totalTimeDays[currentDayOfWeek - indx] = getTotalTime(usageStatsManager, startOfDay, endOfDay);

            calendar.add(Calendar.DAY_OF_YEAR, -1);
        }

        return totalTimeDays;
    }


    private long[] retrieveDataMonths(UsageStatsManager usageStatsManager) { // This creates a array of usage times for each month of the year
        long[] totalTimeMonths = new long[12];

        Calendar calendar = Calendar.getInstance();
        long currentTime = System.currentTimeMillis();

        ExecutorService executorService = Executors.newSingleThreadExecutor();

        executorService.execute(() -> {

        });

        for (int indx = 0; indx < totalTimeMonths.length; indx++) {
            calendar.set(Calendar.MONTH, indx);
            calendar.set(Calendar.DAY_OF_MONTH, 1);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            long startOfMonth = calendar.getTimeInMillis();

            calendar.add(Calendar.MONTH, 1);
            long endOfMonth = calendar.getTimeInMillis();

            if (endOfMonth > currentTime) {
                endOfMonth = currentTime;
            }

            long usageTotal = getTotalTime(usageStatsManager, startOfMonth, endOfMonth);

            final int currentIndx = indx;

            dataBaseMonthlty(startOfMonth, endOfMonth, databaseUsage -> { // This creates a background thread to collect data from the database
                totalTimeMonths[currentIndx] = usageTotal + databaseUsage;
            });


        }

        return totalTimeMonths;
    }

    private void dataBaseMonthlty(long startOfMonth, long endOfMonth, MonthlyDataCallback callback) { // Creates a background to get data from the database
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        executorService.execute(() -> {
            AppDatabase database = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "usage_db").build();
            long databaseUsage = database.usageDao().getTotalUsageBetweenDates(startOfMonth, endOfMonth);

            runOnUiThread(() -> {
                callback.onDataRetrieved(databaseUsage);
            });
        });
    }

    public interface MonthlyDataCallback {
        void onDataRetrieved(long databaseUsage);
    }

    public class AppUsageInfo { // Used for collecting data on each app
        String app;
        Drawable appIcon;
        long usageTime;

        public AppUsageInfo(String app, Drawable appIcon, long usageTime) {
            this.app = app;
            this.appIcon = appIcon;
            this.usageTime = usageTime;
        }
    }


    private void getApps(Context context, getAppsCallback callback) { // Creates a background thread where data on each app is collected
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        executorService.execute(() -> {
            List<AppUsageInfo> appUsageInfoList = new ArrayList<>();
            PackageManager packageManager = context.getPackageManager();

            List<ApplicationInfo> apps = packageManager.getInstalledApplications(0);
            UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            long currentTime = System.currentTimeMillis();
            long startTime = currentTime - (1000 * 3600 * 168);

            Map<String, UsageStats> usageStatsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, currentTime);

            for (ApplicationInfo appInfo : apps) {
                String packageName = appInfo.packageName;
                String appName = appInfo.loadLabel(packageManager).toString();
                Drawable appIcon = appInfo.loadIcon(packageManager);


                UsageStats usageStats = usageStatsMap.get(packageName);
                long usageTime = (usageStats != null) ? usageStats.getTotalTimeInForeground() : 0;


                if (usageTime > 0) {
                    appUsageInfoList.add(new AppUsageInfo(appName, appIcon, usageTime));
                    Log.d("AppUsage", "App: " + appName + ", Usage Time: " + usageTime);
                }
            }

            appUsageInfoList.sort((app1, app2) -> Long.compare(app2.usageTime, app1.usageTime));

            runOnUiThread(() -> {
                callback.onAppsRetrieved(appUsageInfoList);
            });

        });
    }

    public interface getAppsCallback {
        void onAppsRetrieved(List<AppUsageInfo> appInfo);
    }


    private long getTotalTime(UsageStatsManager usageStatsManager, long startTime, long endTime) { // Gets the total of availible data
        List<UsageStats> usageStatsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);
        long totalTime = 0;
        for (UsageStats usageStats : usageStatsList) {
            totalTime += usageStats.getTotalTimeInForeground();
        }
        return totalTime;
    }


    public static class timeMetrics { // Used to hold information for all time data
        private final long timeSpentDay;
        private final long timeSpentWeek;
        private final long timeSpentMonth;
        private final long timeSpentYear;
        private final long timeSpentTotal;
        private final long[] timeSpentDays;
        private final long[] timeSpentMonths;

        private timeMetrics(long timeSpentDay, long timeSpentWeek, long timeSpentMonth, long timeSpentYear, long timeSpentTotal, long[] timeSpentDays, long[] timeSpentMonths) {
            this.timeSpentDay = timeSpentDay / (1000 * 3600);
            this.timeSpentWeek = timeSpentWeek / (1000 * 3600);
            this.timeSpentMonth = timeSpentMonth / (1000 * 3600);
            this.timeSpentYear = timeSpentYear / (1000 * 3600);
            this.timeSpentTotal = timeSpentTotal / (1000 * 3600);
            this.timeSpentDays = timeSpentDays;
            this.timeSpentMonths = timeSpentMonths;
        }

        public long getTimeSpentDay() {
            return timeSpentDay;
        }

        public long getTimeSpentWeek() {
            return timeSpentWeek;
        }

        public long getTimeSpentMonth() {
            return timeSpentMonth;
        }

        public long getTimeSpentYear() {
            return timeSpentYear;
        }

        public long getTimeSpentTotal() {
            return timeSpentTotal;
        }

        public long[] getTimeSpentDays() {
            return timeSpentDays;
        }

        public long[] getTimeSpentMonths() {
            return timeSpentMonths;
        }

        public int booksRead() { return (int)timeSpentTotal / 29880; }

        public int skillsLearnt() {
            return (int)timeSpentTotal / 90000;
        }

        public int degreesLearnt() {
            return (int)timeSpentTotal / 4320000;
        }

        public int languagesLearnt() {
            return (int)timeSpentTotal / 2160000;
        }

        public int  instrumentLearnt() {
            return (int)timeSpentTotal / 7200000;
        }



    }


    private void dataCollectionSchedule() { //Sets up a work manager to collect data for the database at midnight everyday
        WorkManager workManager = WorkManager.getInstance(this);
        PeriodicWorkRequest dailyWorkRequest = new PeriodicWorkRequest.Builder(DailyUsageWorker.class, 1, TimeUnit.DAYS)
                .setInitialDelay(midnightDelay(), TimeUnit.MILLISECONDS).build();

        workManager.enqueueUniquePeriodicWork(
                "DailyUsageWork",
                ExistingPeriodicWorkPolicy.KEEP,
                dailyWorkRequest
        );

    }

    private long midnightDelay() { // This is used to set up the wait for the work manager to collect data at midnight
        Calendar current = Calendar.getInstance();
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        if (current.after(midnight)) {
            midnight.add(Calendar.DAY_OF_YEAR, 1);
        }

        return midnight.getTimeInMillis() - current.getTimeInMillis();
    }


    public class DailyUsageWorker extends Worker { // This is the worker class

        public DailyUsageWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
            super(context, workerParams);
        }

        private void dataCollection() { // This collects the hours spent in the day and stores it in the database
            UsageStatsManager usageStatsManager = (UsageStatsManager) getApplicationContext().getSystemService(Context.USAGE_STATS_SERVICE);

            Calendar calendar = Calendar.getInstance();
            long endOfDay = calendar.getTimeInMillis();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            long startOfDay = calendar.getTimeInMillis();
            long totalTimeToday = getTotalTime(usageStatsManager, startOfDay, endOfDay);


            AppDatabase database = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "Usage_db").build();

            DailyUsage existingData = database.usageDao().getUsageForDay(startOfDay);
            if (existingData == null) {
                DailyUsage dailyUsage = new DailyUsage();
                dailyUsage.date = startOfDay;
                dailyUsage.usageTime = totalTimeToday;
                database.usageDao().insertDailyUsage(dailyUsage);
            } else {
                existingData.usageTime = totalTimeToday;
                database.usageDao().insertDailyUsage(existingData);
            }

        }

        @NonNull
        @Override
        public Result doWork() {
            dataCollection();
            return Result.success();
        }
    }

    public class MyAdapter extends RecyclerView.Adapter<MyAdapter.MyViewHolder> { // This is the adapter for the recycler view to display the apps
        private List<AppUsageInfo> appUsageInfoList;
        private Context context;

        public MyAdapter(List<AppUsageInfo> appUsageInfoList, Context context) {
            this.appUsageInfoList = appUsageInfoList;
            this.context = context;
        }

        @NonNull
        @Override
        public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.app_layout, parent, false);
            return new MyViewHolder(view);
        }

        public class MyViewHolder extends RecyclerView.ViewHolder {
            public ImageView appIcon;
            public TextView appName, appUsage;
            public MyViewHolder(@NonNull View itemView) {
                super(itemView);
                appIcon = itemView.findViewById(R.id.appIcon);
                appName = itemView.findViewById(R.id.app_name);
                appUsage = itemView.findViewById(R.id.usage_time);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull MyAdapter.MyViewHolder holder, int position) {
            AppUsageInfo appUsageInfo = appUsageInfoList.get(position);
            holder.appName.setText(appUsageInfo.app);
            holder.appIcon.setImageDrawable(appUsageInfo.appIcon);

            String usageTime = formatUsageTime(appUsageInfo.usageTime);
            holder.appUsage.setText(usageTime);
        }

        @Override
        public int getItemCount() {
            return appUsageInfoList.size();
        }


        private String formatUsageTime(long usageTime) {
            long seconds = (usageTime / 1000) % 60;
            long minutes = (usageTime / (1000 * 60)) % 60;
            long hours = (usageTime / (1000 * 60 * 60)) % 24;
            return String.format("%02d:%02d:%02d", hours, minutes, seconds); 
        }
    }



    @Entity(tableName = "daily_usage") // This is the database
    public static class DailyUsage {
        @PrimaryKey(autoGenerate = true)
        public int id;
        public long date;
        public long usageTime;
    }

    @Dao
    public interface UsageDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        void insertDailyUsage(DailyUsage dailyUsage);

        @Query("SELECT * FROM daily_usage WHERE date = :day")
        DailyUsage getUsageForDay(long day);

        @Query("SELECT SUM(usageTime) FROM daily_usage WHERE date BETWEEN :startDate AND :endDate")
        long getTotalUsageBetweenDates(long startDate, long endDate);
    }

    @Database(entities = {DailyUsage.class}, version = 1)
    public abstract static class AppDatabase extends RoomDatabase {
        public abstract UsageDao usageDao();
    }

}