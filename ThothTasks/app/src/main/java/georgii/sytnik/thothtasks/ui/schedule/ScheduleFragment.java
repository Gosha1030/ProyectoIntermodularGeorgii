package georgii.sytnik.thothtasks.ui.schedule;

import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GestureDetectorCompat;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import georgii.sytnik.thothtasks.R;
import georgii.sytnik.thothtasks.databinding.FragmentScheduleBinding;

public class ScheduleFragment extends Fragment {

    private FragmentScheduleBinding binding;
    private GestureDetectorCompat detector;

    private Mode mode = Mode.WEEK;
    private Calendar currentDate = Calendar.getInstance();

    private enum Mode {
        DAY, WEEK, MONTH, YEAR
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentScheduleBinding.inflate(inflater, container, false);

        detector = new GestureDetectorCompat(requireContext(), new GestureListener());

        binding.scheduleSurface.setOnTouchListener((v, event) -> detector.onTouchEvent(event));

        refreshHeader();
        return binding.getRoot();
    }

    private void refreshHeader() {
        binding.tvMode.setText(getModeLabel());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        binding.tvCurrentDate.setText(sdf.format(currentDate.getTime()));
    }

    private String getModeLabel() {
        switch (mode) {
            case DAY:
                return getString(R.string.mode_day);
            case WEEK:
                return getString(R.string.mode_week);
            case MONTH:
                return getString(R.string.mode_month);
            case YEAR:
                return getString(R.string.mode_year);
            default:
                return "";
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onDown(@NonNull MotionEvent e) {
            return true;
        }

        @Override
        public boolean onFling(@NonNull MotionEvent e1, @NonNull MotionEvent e2,
                               float velocityX, float velocityY) {

            float diffX = e2.getX() - e1.getX();
            float diffY = e2.getY() - e1.getY();

            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        goPrevious();
                    } else {
                        goNext();
                    }
                    return true;
                }
            } else {
                if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) {
                        changeModeDown();
                    } else {
                        changeModeUp();
                    }
                    return true;
                }
            }

            return false;
        }
    }

    private void goPrevious() {
        switch (mode) {
            case DAY:
                currentDate.add(Calendar.DAY_OF_MONTH, -1);
                break;
            case WEEK:
                currentDate.add(Calendar.WEEK_OF_YEAR, -1);
                break;
            case MONTH:
                currentDate.add(Calendar.MONTH, -1);
                break;
            case YEAR:
                currentDate.add(Calendar.YEAR, -1);
                break;
        }
        refreshHeader();
    }

    private void goNext() {
        switch (mode) {
            case DAY:
                currentDate.add(Calendar.DAY_OF_MONTH, 1);
                break;
            case WEEK:
                currentDate.add(Calendar.WEEK_OF_YEAR, 1);
                break;
            case MONTH:
                currentDate.add(Calendar.MONTH, 1);
                break;
            case YEAR:
                currentDate.add(Calendar.YEAR, 1);
                break;
        }
        refreshHeader();
    }

    private void changeModeUp() {
        switch (mode) {
            case DAY:
                mode = Mode.WEEK;
                break;
            case WEEK:
                mode = Mode.MONTH;
                break;
            case MONTH:
                mode = Mode.YEAR;
                break;
            case YEAR:
                mode = Mode.YEAR;
                break;
        }
        refreshHeader();
    }

    private void changeModeDown() {
        switch (mode) {
            case YEAR:
                mode = Mode.MONTH;
                break;
            case MONTH:
                mode = Mode.WEEK;
                break;
            case WEEK:
                mode = Mode.DAY;
                break;
            case DAY:
                mode = Mode.DAY;
                break;
        }
        refreshHeader();
    }
}