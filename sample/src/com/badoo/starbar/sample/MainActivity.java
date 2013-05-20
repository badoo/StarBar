package com.badoo.starbar.sample;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.badoo.starbar.StarBar;
import com.badoo.starbar.StarBar.OnRatingSliderChangeListener;

public class MainActivity extends Activity implements OnRatingSliderChangeListener, OnCheckedChangeListener {

    private TextView ratingText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StarBar starBar = (StarBar) findViewById(R.id.starBar);
        starBar.setOnRatingSliderChangeListener(this);

        ratingText = (TextView) findViewById(R.id.rating);

        CheckBox alternativeRangeCheckBox = (CheckBox) findViewById(R.id.rangeCheckBox);
        alternativeRangeCheckBox.setOnCheckedChangeListener(this);
    }

    @Override
    public boolean onStartRating() {
        ratingText.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Started rating", Toast.LENGTH_SHORT).show();
        return true;
    }

    @Override
    public void onPendingRating(int rating) {
        ratingText.setText(Integer.toString(rating));
    }

    @Override
    public void onFinalRating(int rating, boolean swipe) {
        ratingText.setVisibility(View.GONE);
        Toast.makeText(this, "Final rating " + rating, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCancelRating() {
        Toast.makeText(this, "Rating  cancelled", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        StarBar starBar = (StarBar) findViewById(R.id.starBar);
        if (isChecked) {
            // Set alternative ranges {green=1, yellow=2-3, green=4-10}
            starBar.setRanges(2, 4);
        }
        else {
            // Restore the default ranges {green=1-3, yellow=4-6, green=7-10}
            starBar.setRanges(4, 7);
        }
    }

}
