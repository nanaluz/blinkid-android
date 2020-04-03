package com.microblink.blinkid;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import com.microblink.entities.recognizers.Recognizer;
import com.microblink.entities.recognizers.RecognizerBundle;
import com.microblink.entities.recognizers.blinkid.generic.BlinkIdCombinedRecognizer;
import com.microblink.fragment.RecognizerRunnerFragment;
import com.microblink.fragment.overlay.ScanningOverlay;
import com.microblink.fragment.overlay.blinkid.BlinkIdOverlayController;
import com.microblink.recognition.RecognitionSuccessType;
import com.microblink.view.recognition.ScanResultListener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

public class ScanActivity extends AppCompatActivity implements RecognizerRunnerFragment.ScanningOverlayBinder {

    private RecognizerRunnerFragment recognizerRunnerFragment;
    private BlinkIdOverlayController overlayController;
    private RecognizerBundle recognizerBundle;
    private BlinkIdCombinedRecognizer blinkIdCombinedRecognizer;

    private ScanResultListener scanResultListener = new ScanResultListener() {
        @Override
        public void onScanningDone(@NonNull RecognitionSuccessType recognitionSuccessType) {

            BlinkIdCombinedRecognizer.Result blinkIdCombinedRecognizerResult =
                    blinkIdCombinedRecognizer.getResult();
            if (blinkIdCombinedRecognizerResult.getResultState() != Recognizer.Result.State.Valid) {
                return;
            }

            // pause scanning to prevent new results while activity is being shut down
            // or while alert dialog is shown
            recognizerRunnerFragment.getRecognizerRunnerView().pauseScanning();

            if (!isDataMatching(blinkIdCombinedRecognizerResult)) {
                // We want to show the message that the result data is not matching and instruct
                // the user to scan again
                showRepeatScanningAlert(R.string.message_data_match_failed);
            } else if (!isDataValid(blinkIdCombinedRecognizerResult)) {
                showRepeatScanningAlert(R.string.message_data_validation_failed);
            } else {
                Intent intent = new Intent();
                // save recognizer bundle to make it available for loading later
                recognizerBundle.saveToIntent(intent);
                // save HighResImagesBundle, for cases when high res frames are enabled
                overlayController.getHighResImagesBundle().saveToIntent(intent);
                // set activity result
                setResult(RESULT_OK, intent);

                finish();
            }
        }
    };


    private boolean isDataMatching(BlinkIdCombinedRecognizer.Result result) {
        // Personal ID number contains dashes, so we need to remove them before comparing
        // it with the data from the back
        String cleanedPin = result.getPersonalIdNumber().replace("-", "");

        // Personal ID number needs to be equal to OPT1 in the MRZ
        if (!cleanedPin.equals(result.getMrzResult().getOpt1())) {
            return false;
        }

//        // Also, secondary ID from the back needs to be present in the full name
//        String mrzSecondaryId = result.getMrzResult().getSecondaryId();
//        if (mrzSecondaryId.isEmpty() || !(result.getFullName().toUpperCase().contains(mrzSecondaryId))) {
//            return false;
//        }

        // data is matching if all previous conditions are true
        return true;
    }

    private boolean isDataValid(BlinkIdCombinedRecognizer.Result result) {
        // Full name must not be empty, and allowed characters are:
        // - all characters from the English alphabet: A-Z and a-z
        // - character '-'
        // - all whitespace characters
        return (result.getFullName()).matches("[a-zA-Z|\\-|\\s]+");
    }

    private void showRepeatScanningAlert(@StringRes int messageResourceId) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_retry_title)
                .setMessage(messageResourceId)
                .setPositiveButton(R.string.dialog_retry_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // resume scanning and reset scanning state
                        recognizerRunnerFragment.getRecognizerRunnerView().resumeScanning(true);
                    }
                })
                .create();
        dialog.show();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // setup recognizer and put it into recognizer bundle
        blinkIdCombinedRecognizer = new BlinkIdCombinedRecognizer();
        blinkIdCombinedRecognizer.setReturnFullDocumentImage(true);
        blinkIdCombinedRecognizer.setReturnFaceImage(true);
        recognizerBundle = new RecognizerBundle(blinkIdCombinedRecognizer);

        overlayController = BlinkIdOverlayControllerBuilder.build(this, recognizerBundle, scanResultListener);

        // scanning overlay must be created before restoring fragment state
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scan_activity);

        if (null == savedInstanceState) {
            // create fragment transaction to replace R.id.recognizer_runner_view_container with RecognizerRunnerFragment
            recognizerRunnerFragment = new RecognizerRunnerFragment();
            FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(com.microblink.library.R.id.recognizer_runner_view_container, recognizerRunnerFragment);
            fragmentTransaction.commit();
        } else {
            // obtain reference to fragment restored by Android within super.onCreate() call
            recognizerRunnerFragment = (RecognizerRunnerFragment) getSupportFragmentManager().findFragmentById(R.id.recognizer_runner_view_container);
        }
    }

    @Override
    public void onBackPressed() {
        // user cancels scanning by pressing back button
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }

    @NonNull
    @Override
    public ScanningOverlay getScanningOverlay() {
        return overlayController;
    }

}
