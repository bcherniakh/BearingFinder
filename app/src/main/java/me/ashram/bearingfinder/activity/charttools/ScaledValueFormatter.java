package me.ashram.bearingfinder.activity.charttools;

import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ViewPortHandler;

import java.text.DecimalFormat;

/**
 * Created by Cherniakh Bohdan on 25.12.2015.
 */
public class ScaledValueFormatter implements ValueFormatter {

    private DecimalFormat mFormat;

    public ScaledValueFormatter() {
        mFormat = new DecimalFormat("###,###,##0.0"); // use one decimal
    }

    @Override
    public String getFormattedValue(float value, Entry entry, int dataSetIndex, ViewPortHandler viewPortHandler) {
        // write your logic here
        value -= 120;
        return mFormat.format(value) + " dB"; // e.g. append a dollar-sign
    }
}
