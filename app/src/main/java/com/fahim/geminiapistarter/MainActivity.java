package com.fahim.geminiapistarter;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.type.GenerateContentResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;

//import android.content.SharedPreferences;
//import com.google.gson.Gson;
//import com.google.gson.reflect.TypeToken;

public class MainActivity extends AppCompatActivity {

    private EditText promptEditText;
    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private MessageAdapter messageAdapter;
    private List<Message> messageList = new ArrayList<>();
    private SpeechRecognizer speechRecognizer;
    private GenerativeModel generativeModel;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1;

    private static final String PREFS_NAME = "ChatPrefs";
    private static final String CHAT_HISTORY_KEY = "chat_history";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        // Adjust layout to avoid overlap with system bars
        View rootView = findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;

            v.setPadding(0, statusBarHeight, 0, navBarHeight);
            return insets;
        });
        promptEditText = findViewById(R.id.promptEditText);
        ImageButton sendButton = findViewById(R.id.sendButton);
        ImageButton voiceInputButton = findViewById(R.id.voiceButton);
        progressBar = findViewById(R.id.progressBar);
        recyclerView = findViewById(R.id.recyclerView);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        messageAdapter = new MessageAdapter(messageList);
        recyclerView.setAdapter(messageAdapter);

        // Load previous chat messages
//        loadChatHistory();

        // Request microphone permission
        requestMicrophonePermission();

        // Initialize SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);

        // Initialize Generative AI Model (Gemini)
        generativeModel = new GenerativeModel("gemini-2.0-flash", BuildConfig.API_KEY);

        voiceInputButton.setOnClickListener(v -> startVoiceRecognition());

        sendButton.setOnClickListener(v -> sendMessage());



//        promptEditText.setOnFocusChangeListener((v, hasFocus) -> {
//            if (hasFocus) {
//                scrollToBottom();
//            }
//        });

    }

    private void sendMessage() {

        String prompt = promptEditText.getText().toString().trim();
        if (prompt.isEmpty()) {
            promptEditText.setError("Field cannot be empty");
            return;
        }

        // Add user message to RecyclerView
        messageList.add(new Message(prompt, true));
        messageAdapter.notifyItemInserted(messageList.size() - 1);
        recyclerView.scrollToPosition(messageList.size() - 1);
        promptEditText.setText(""); // Clear input field

        // Show progress bar
        progressBar.setVisibility(View.VISIBLE);


        // Send request to Gemini AI
        generativeModel.generateContent(prompt, new Continuation<>() {
            @NonNull
            @Override
            public CoroutineContext getContext() {
                return EmptyCoroutineContext.INSTANCE;
            }

            @Override
            public void resumeWith(@NonNull Object o) {
                if (o instanceof GenerateContentResponse) {
                    GenerateContentResponse response = (GenerateContentResponse) o;
                    String responseString = response.getText();

                    // Check for null response
                    if (responseString == null || responseString.isEmpty()) {
                        responseString = "No response received.";
                    }

                    Log.d("Gemini Response", responseString);

                    // Update UI on main thread
                    String finalResponseString = responseString;
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        messageList.add(new Message(finalResponseString, false));
                        messageAdapter.notifyItemInserted(messageList.size() - 1);
                        recyclerView.scrollToPosition(messageList.size() - 1);
                        // Save updated chat history
//                        saveChatHistory();
                    });
                } else {
                    Log.e("Gemini Error", "Unexpected response type");
                }
            }

        });


    }
    // Save messages to SharedPreferences
//    private void saveChatHistory() {
//        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
//        SharedPreferences.Editor editor = prefs.edit();
//        Gson gson = new Gson();
//        String json = gson.toJson(messageList);
//        editor.putString(CHAT_HISTORY_KEY, json);
//        editor.apply();
//    }
//
//    // Load messages from SharedPreferences
//    private void loadChatHistory() {
//        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
//        Gson gson = new Gson();
//        String json = prefs.getString(CHAT_HISTORY_KEY, null);
//        if (json != null) {
//            messageList = gson.fromJson(json, new TypeToken<List<Message>>() {}.getType());
//            messageAdapter.notifyDataSetChanged();
//        }
//    }
    // Request microphone permission
    private void requestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        }
    }

    // Handle permission result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Microphone permission is required for speech input", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Start voice recognition
    private void startVoiceRecognition() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestMicrophonePermission();
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...");

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d("SpeechRecognizer", "Ready for speech");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d("SpeechRecognizer", "Speech started");
            }

            @Override
            public void onRmsChanged(float rmsdB) {

            }

            @Override
            public void onBufferReceived(byte[] buffer) {

            }

            @Override
            public void onEndOfSpeech() {
                Log.d("SpeechRecognizer", "Speech ended");
            }

            @Override
            public void onError(int error) {
                Toast.makeText(MainActivity.this, "Speech recognition failed. Try again!", Toast.LENGTH_SHORT).show();
                Log.e("SpeechRecognizer", "Error: " + error);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    promptEditText.setText(matches.get(0)); // Set recognized text in EditText
                    sendMessage(); // Auto-send message
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });

        speechRecognizer.startListening(intent);
    }
//    private void scrollToBottom() {
//        recyclerView.post(() -> recyclerView.smoothScrollToPosition(messageList.size() - 1));
//    }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroy();
    }
}
