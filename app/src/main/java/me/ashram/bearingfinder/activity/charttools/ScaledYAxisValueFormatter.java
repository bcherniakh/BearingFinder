package me.ashram.bearingfinder.activity.charttools;

/**
 * Created by Cherniakh Bohdan on 22.12.2015.
 */
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.formatter.YAxisValueFormatter;

import java.text.DecimalFormat;

public class ScaledYAxisValueFormatter implements YAxisValueFormatter {

    private DecimalFormat mFormat;

    public ScaledYAxisValueFormatter() {
        mFormat = new DecimalFormat("###,###,###,##0.0");
    }

    @Override
    public String getFormattedValue(float value, YAxis yAxis) {
        value -=120;
        return mFormat.format(value) + " dB";
    }
}