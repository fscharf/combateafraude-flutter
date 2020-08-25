package com.combateafraude.document_detector;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.combateafraude.documentdetector.DocumentDetector;
import com.combateafraude.documentdetector.DocumentDetectorActivity;
import com.combateafraude.documentdetector.DocumentDetectorResult;
import com.combateafraude.documentdetector.configuration.CaptureMode;
import com.combateafraude.documentdetector.configuration.CaptureStage;
import com.combateafraude.documentdetector.configuration.DetectionSettings;
import com.combateafraude.documentdetector.configuration.Document;
import com.combateafraude.documentdetector.configuration.DocumentDetectorStep;
import com.combateafraude.documentdetector.configuration.QualitySettings;
import com.combateafraude.documentdetector.controller.Capture;
import com.combateafraude.helpers.sdk.failure.AvailabilityReason;
import com.combateafraude.helpers.sdk.failure.InvalidTokenReason;
import com.combateafraude.helpers.sdk.failure.LibraryReason;
import com.combateafraude.helpers.sdk.failure.NetworkReason;
import com.combateafraude.helpers.sdk.failure.PermissionReason;
import com.combateafraude.helpers.sdk.failure.SDKFailure;
import com.combateafraude.helpers.sdk.failure.SecurityReason;
import com.combateafraude.helpers.sdk.failure.ServerReason;
import com.combateafraude.helpers.sdk.failure.StorageReason;
import com.combateafraude.helpers.sensors.SensorLuminositySettings;
import com.combateafraude.helpers.sensors.SensorOrientationSettings;
import com.combateafraude.helpers.sensors.SensorStabilitySettings;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import java.util.ArrayList;
import java.util.HashMap;

@SuppressWarnings("unchecked")
public class DocumentDetectorPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.ActivityResultListener {

    private static final int REQUEST_CODE = 1001;

    private static final String DRAWABLE_RES = "drawable";
    private static final String STYLE_RES = "style";
    private static final String STRING_RES = "string";
    private static final String RAW_RES = "raw";
    private static final String LAYOUT_RES = "layout";

    private MethodChannel channel;
    private Result result;
    private Activity activity;
    private ActivityPluginBinding activityBinding;
    private Context context;

    @Override
    public synchronized void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        if (call.method.equals("start")) {
            this.result = result;
            start(call);
        } else {
            result.notImplemented();
        }
    }

    private synchronized void start(@NonNull MethodCall call) {
        HashMap<String, Object> argumentsMap = (HashMap<String, Object>) call.arguments;

        // Mobile token
        String mobileToken = (String) argumentsMap.get("mobileToken");

        DocumentDetector.Builder mDocumentDetectorBuilder = new DocumentDetector.Builder(mobileToken);

        // Document steps
        ArrayList<HashMap<String, Object>> paramSteps = (ArrayList<HashMap<String, Object>>) argumentsMap.get("documentSteps");
        DocumentDetectorStep[] documentDetectorSteps = new DocumentDetectorStep[paramSteps.size()];
        for (int i = 0; i < paramSteps.size(); i++) {
            HashMap<String, Object> step = paramSteps.get(i);

            Document document = Document.valueOf((String) step.get("document"));

            HashMap<String, Object> stepCustomization = (HashMap<String, Object>) step.get("android");
            if (stepCustomization != null) {
                Integer stepLabelId = getResourceId((String) stepCustomization.get("stepLabelStringResName"), STRING_RES);
                Integer illustrationId = getResourceId((String) stepCustomization.get("illustrationDrawableResName"), DRAWABLE_RES);
                Integer audioId = getResourceId((String) stepCustomization.get("audioRawResName"), RAW_RES);

                documentDetectorSteps[i] = new DocumentDetectorStep(document, stepLabelId, illustrationId, audioId);
            } else {
                documentDetectorSteps[i] = new DocumentDetectorStep(document, null, null, null);
            }
        }
        mDocumentDetectorBuilder.setDocumentSteps(documentDetectorSteps);


        // Android specific settings
        HashMap<String, Object> androidSettings = (HashMap<String, Object>) argumentsMap.get("androidSettings");
        if (androidSettings != null) {

            // Capture stages
            ArrayList<HashMap<String, Object>> paramStages = (ArrayList<HashMap<String, Object>>) androidSettings.get("captureStages");
            if (paramStages != null) {
                CaptureStage[] captureStages = new CaptureStage[paramStages.size()];
                for (int i = 0; i < paramStages.size(); i++) {
                    HashMap<String, Object> stage = paramStages.get(i);

                    Long durationMillis = (Long) stage.get("durationMillis");
                    Boolean wantSensorCheck = (Boolean) stage.get("wantSensorCheck");
                    if (wantSensorCheck == null) wantSensorCheck = false;

                    QualitySettings qualitySettings = null;
                    HashMap<String, Object> qualitySettingsParam = (HashMap<String, Object>) stage.get("qualitySettings");
                    if (qualitySettingsParam != null) {
                        Double threshold = (Double) qualitySettingsParam.get("threshold");
                        if (threshold == null) threshold = QualitySettings.RECOMMENDED_THRESHOLD;
                        qualitySettings = new QualitySettings(threshold);
                    }

                    DetectionSettings detectionSettings = null;
                    HashMap<String, Object> detectionSettingsParam = (HashMap<String, Object>) stage.get("detectionSettings");
                    if (detectionSettingsParam != null) {
                        Double threshold = (Double) detectionSettingsParam.get("threshold");
                        if (threshold == null) threshold = DetectionSettings.RECOMMENDED_THRESHOLD;
                        Integer consecutiveFrames = (Integer) detectionSettingsParam.get("consecutiveFrames");
                        if (consecutiveFrames == null) consecutiveFrames = 5;
                        detectionSettings = new DetectionSettings(threshold, consecutiveFrames);
                    }
                    CaptureMode captureMode = CaptureMode.valueOf((String) stage.get("captureMode"));

                    captureStages[i] = new CaptureStage(durationMillis, wantSensorCheck, qualitySettings, detectionSettings, captureMode);
                }
                mDocumentDetectorBuilder.setCaptureStages(captureStages);
            }

            // Layout customization
            HashMap<String, Object> customizationAndroid = (HashMap<String, Object>) androidSettings.get("customization");
            if (customizationAndroid != null) {
                Integer styleId = getResourceId((String) customizationAndroid.get("styleResIdName"), STYLE_RES);
                if (styleId != null) mDocumentDetectorBuilder.setStyle(styleId);

                Integer layoutId = getResourceId((String) customizationAndroid.get("layoutResIdName"), LAYOUT_RES);
                Integer greenMaskId = getResourceId((String) customizationAndroid.get("greenMaskResIdName"), DRAWABLE_RES);
                Integer whiteMaskId = getResourceId((String) customizationAndroid.get("whiteMaskResIdName"), DRAWABLE_RES);
                Integer redMaskId = getResourceId((String) customizationAndroid.get("redMaskResIdName"), DRAWABLE_RES);
                mDocumentDetectorBuilder.setLayout(layoutId, greenMaskId, whiteMaskId, redMaskId);
            }

            // Sensor settings
            HashMap<String, Object> sensorSettings = (HashMap<String, Object>) androidSettings.get("sensorSettings");
            if (sensorSettings != null) {
                HashMap<String, Object> sensorLuminosity = (HashMap<String, Object>) sensorSettings.get("sensorLuminositySettings");
                if (sensorLuminosity != null) {
                    Integer sensorMessageId = getResourceId((String) sensorLuminosity.get("messageResourceIdName"), STRING_RES);
                    Integer luminosityThreshold = (Integer) sensorLuminosity.get("luminosityThreshold");
                    if (sensorMessageId != null && luminosityThreshold != null) {
                        mDocumentDetectorBuilder.setLuminositySensorSettings(new SensorLuminositySettings(sensorMessageId, luminosityThreshold));
                    }
                } else {
                    mDocumentDetectorBuilder.setLuminositySensorSettings(null);
                }

                HashMap<String, Object> sensorOrientation = (HashMap<String, Object>) sensorSettings.get("sensorOrientationSettings");
                if (sensorOrientation != null) {
                    Integer sensorMessageId = getResourceId((String) sensorOrientation.get("messageResourceIdName"), STRING_RES);
                    Double orientationThreshold = (Double) sensorOrientation.get("orientationThreshold");
                    if (sensorMessageId != null && orientationThreshold != null) {
                        mDocumentDetectorBuilder.setOrientationSensorSettings(new SensorOrientationSettings(sensorMessageId, orientationThreshold));
                    }
                } else {
                    mDocumentDetectorBuilder.setOrientationSensorSettings(null);
                }

                HashMap<String, Object> sensorStability = (HashMap<String, Object>) sensorSettings.get("sensorStabilitySettings");
                if (sensorStability != null) {
                    Integer sensorMessageId = getResourceId((String) sensorStability.get("messageResourceIdName"), STRING_RES);
                    Integer stabilityStabledMillis = (Integer) sensorStability.get("stabilityStabledMillis");
                    Double stabilityThreshold = (Double) sensorStability.get("stabilityThreshold");
                    if (sensorMessageId != null && stabilityStabledMillis != null && stabilityThreshold != null) {
                        mDocumentDetectorBuilder.setStabilitySensorSettings(new SensorStabilitySettings(sensorMessageId, stabilityStabledMillis, stabilityThreshold));
                    }
                } else {
                    mDocumentDetectorBuilder.setStabilitySensorSettings(null);
                }
            }
        }

        // Popup settings
        Boolean showPopup = (Boolean) argumentsMap.get("popup");
        if (showPopup != null) mDocumentDetectorBuilder.setPopupSettings(showPopup);

        // Sound settings
        Boolean enableSound = (Boolean) argumentsMap.get("sound");
        if (enableSound != null) mDocumentDetectorBuilder.enableSound(enableSound);

        // Network settings
        Integer requestTimeout = (Integer) argumentsMap.get("requestTimeout");
        if (requestTimeout != null) mDocumentDetectorBuilder.setNetworkSettings(requestTimeout);

        Intent mIntent = new Intent(context, DocumentDetectorActivity.class);
        mIntent.putExtra(DocumentDetector.PARAMETER_NAME, mDocumentDetectorBuilder.build());
        activity.startActivityForResult(mIntent, REQUEST_CODE);
    }

    private Integer getResourceId(@Nullable String resourceName, String resourceType) {
        if (resourceName == null || activity == null) return null;
        int resId = activity.getResources().getIdentifier(resourceName, resourceType, activity.getPackageName());
        return resId == 0 ? null : resId;
    }

    private HashMap<String, Object> getSucessResponseMap(DocumentDetectorResult mDocumentDetectorResult) {
        HashMap<String, Object> responseMap = new HashMap<>();
        responseMap.put("success", Boolean.TRUE);
        ArrayList<HashMap<String, Object>> captures = new ArrayList<>();
        for (Capture capture : mDocumentDetectorResult.getCaptures()) {
            HashMap<String, Object> captureResponse = new HashMap<>();
            captureResponse.put("imagePath", capture.getImagePath());
            captureResponse.put("imageUrl", capture.getImageUrl());
            captureResponse.put("label", capture.getLabel());
            captureResponse.put("quality", capture.getQuality());
            captures.add(captureResponse);
        }
        responseMap.put("captures", captures);
        responseMap.put("type", mDocumentDetectorResult.getType());
        return responseMap;
    }

    private HashMap<String, Object> getFailureResponseMap(SDKFailure sdkFailure) {
        HashMap<String, Object> responseMap = new HashMap<>();
        responseMap.put("success", Boolean.FALSE);
        responseMap.put("message", sdkFailure.getMessage());
        responseMap.put("type", sdkFailure.getClass().getSimpleName());
        return responseMap;
    }

    private HashMap<String, Object> getClosedResponseMap() {
        HashMap<String, Object> responseMap = new HashMap<>();
        responseMap.put("success", null);
        return responseMap;
    }

    @Override
    public synchronized boolean onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                DocumentDetectorResult mDocumentDetectorResult = (DocumentDetectorResult) data.getSerializableExtra(DocumentDetectorResult.PARAMETER_NAME);
                if (mDocumentDetectorResult.wasSuccessful()) {
                    if (result != null)
                        result.success(getSucessResponseMap(mDocumentDetectorResult));
                } else {
                    if (result != null)
                        result.success(getFailureResponseMap(mDocumentDetectorResult.getSdkFailure()));
                }
            } else {
                if (result != null) result.success(getClosedResponseMap());
            }
        }
        return false;
    }

    @Override
    public synchronized void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        this.context = flutterPluginBinding.getApplicationContext();
        this.channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "document_detector");
        this.channel.setMethodCallHandler(this);
    }

    @Override
    public synchronized void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        this.channel.setMethodCallHandler(null);
        this.context = null;
    }

    @Override
    public synchronized void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        this.activity = binding.getActivity();
        this.activityBinding = binding;
        this.activityBinding.addActivityResultListener(this);
    }

    @Override
    public synchronized void onDetachedFromActivityForConfigChanges() {

    }

    @Override
    public synchronized void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        this.activity = binding.getActivity();
    }

    @Override
    public synchronized void onDetachedFromActivity() {
        this.activity = null;
        this.activityBinding.removeActivityResultListener(this);
        this.activityBinding = null;
    }
}