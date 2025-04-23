    package com.example.langhexx.View;

    import android.annotation.SuppressLint;
    import android.content.Intent;
    import android.content.pm.PackageManager;
    import android.os.Bundle;
    import android.os.Handler;
    import android.os.Looper;
    import android.speech.RecognitionListener;
    import android.speech.RecognizerIntent;
    import android.speech.SpeechRecognizer;
    import android.speech.tts.TextToSpeech;
    import android.view.LayoutInflater;
    import android.view.View;
    import android.widget.ImageButton;
    import android.widget.ImageView;
    import android.widget.LinearLayout;
    import android.widget.TextView;
    import android.widget.Toast;

    import androidx.activity.EdgeToEdge;
    import androidx.annotation.NonNull;
    import androidx.annotation.Nullable;
    import androidx.appcompat.app.AppCompatActivity;
    import androidx.core.graphics.Insets;
    import androidx.core.view.ViewCompat;
    import androidx.core.view.WindowInsetsCompat;

    import com.example.langhexx.R;
    import com.google.firebase.database.DataSnapshot;
    import com.google.firebase.database.DatabaseError;
    import com.google.firebase.database.DatabaseReference;
    import com.google.firebase.database.FirebaseDatabase;
    import com.google.firebase.database.ValueEventListener;

    import java.util.ArrayList;
    import java.util.Locale;

    //demo
    public class InternalSpeakingTopic extends AppCompatActivity {
        private LinearLayout questionContainer;
        private ArrayList<String> questionList;
        private TextToSpeech textToSpeech;
        private String levelName, topicTitle;
        private int currentQuestionIndex = 0;
        private LayoutInflater inflater;
        private ImageButton btnMicro ;
        private Handler handler = new Handler(Looper.getMainLooper());
        private static final int REQUEST_CODE_SPEECH_INPUT = 100;
        private TextView txtQuestion ;
        private ImageView speaker ;
        private TextView txtResponse ;
        private ImageView responseSpeaker ;
        private ImageView avatarUser ;
        private View qaView ;
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_internal_speaking_topic);
            //
            addControls();
            //
            initTextToSpeech();
        }

        private void initTextToSpeech(){
            textToSpeech = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    int result = textToSpeech.setLanguage(Locale.US); // Locale.UK nếu muốn giọng Anh
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Toast.makeText(this, "Ngôn ngữ không được hỗ trợ!", Toast.LENGTH_SHORT).show();
                    } else {
                        textToSpeech.setPitch(1.1f);        // Cao độ giọng nói
                        textToSpeech.setSpeechRate(0.95f);  // Tốc độ nói
                        loadQuestionsFromFirebase();
                    }
                } else {
                    Toast.makeText(this, "Không thể khởi tạo TextToSpeech!", Toast.LENGTH_SHORT).show();
                }
            });
        }
        private void addControls(){
            questionContainer = findViewById(R.id.questionContainer);
            inflater = LayoutInflater.from(this);
            btnMicro = findViewById(R.id.btnSpeakingMicro) ;
            levelName = getIntent().getStringExtra("levelName");
            topicTitle = getIntent().getStringExtra("topicTitle");
            questionList = new ArrayList<>();
            //
            // Inflate layout custom câu hỏi
            qaView = inflater.inflate(R.layout.custom_question_list, questionContainer, false);
            txtQuestion = qaView.findViewById(R.id.txtQuestion);
            speaker = qaView.findViewById(R.id.speaker);
            txtResponse = qaView.findViewById(R.id.tvResponse);
            responseSpeaker = qaView.findViewById(R.id.responseSpeaker);
            avatarUser = qaView.findViewById(R.id.userAvatar); // avatar người trả lời
        }

        private void loadQuestionsFromFirebase() {
            DatabaseReference questionRef = FirebaseDatabase.getInstance("https://englishlearningapp-7bdec-default-rtdb.asia-southeast1.firebasedatabase.app/")
                    .getReference("Lessons")
                    .child("Levels")
                    .child(levelName)
                    .child("Speaking")
                    .child("Topics")
                    .child(topicTitle);

            questionRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    for (DataSnapshot questionSnap : snapshot.getChildren()) {
                        String question = questionSnap.getValue(String.class);
                        if (question != null) {
                            questionList.add(question);
                        }
                    }

                    if (!questionList.isEmpty()) {
                        showNextQuestion();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(InternalSpeakingTopic.this, "Data Error", Toast.LENGTH_SHORT).show();
                }
            });
        }
        private void showNextQuestion() {
            if (currentQuestionIndex >= questionList.size()) return;

            // Inflate một view mới cho mỗi câu hỏi để không bị ghi đè
            View newQaView = inflater.inflate(R.layout.custom_question_list, questionContainer, false);
            TextView txtQ = newQaView.findViewById(R.id.txtQuestion);
            ImageView speakerQ = newQaView.findViewById(R.id.speaker);
            TextView txtRes = newQaView.findViewById(R.id.tvResponse);
            ImageView resSpeaker = newQaView.findViewById(R.id.responseSpeaker);
            ImageView avatar = newQaView.findViewById(R.id.userAvatar);
            //
            txtRes.setVisibility(View.GONE);
            resSpeaker.setVisibility(View.GONE);
            avatar.setVisibility(View.GONE);
            //
            String question = questionList.get(currentQuestionIndex);
            txtQ.setText(question);

            // Thêm view vào container
            questionContainer.addView(newQaView);

            // Gán listener cho loa câu hỏi
            speakerQ.setOnClickListener(v -> {
                textToSpeech.speak(question, TextToSpeech.QUEUE_FLUSH, null, null);
            });

            // Lưu lại các view để xử lý sau trong onActivityResult
            this.txtResponse = txtRes;
            this.responseSpeaker = resSpeaker;
            this.avatarUser = avatar;

            // Gán sự kiện mic
            btnMicro.setOnClickListener(v -> {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

                try {
                    startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
                } catch (Exception e) {
                    Toast.makeText(InternalSpeakingTopic.this, "Speech recognition is not available", Toast.LENGTH_SHORT).show();
                }
            });
        }



        @Override
        protected void onDestroy() {
            super.onDestroy();
            if (textToSpeech != null) {
                textToSpeech.shutdown();
            }
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK && data != null) {
                ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (results != null && !results.isEmpty()) {
                    String response = results.get(0).trim();
                    if (!response.isEmpty()) {
                        String capitalized = response.substring(0, 1).toUpperCase() + response.substring(1);
                        txtResponse.setText(capitalized);
                        txtResponse.setVisibility(View.VISIBLE);
                        responseSpeaker.setVisibility(View.VISIBLE);
                        avatarUser.setVisibility(View.VISIBLE);

                        // Đọc lại phản hồi nếu muốn
                        responseSpeaker.setOnClickListener(v -> {
                            textToSpeech.speak(capitalized, TextToSpeech.QUEUE_FLUSH, null, null);
                        });

                        // Chuyển sang câu hỏi tiếp theo
                        currentQuestionIndex++;
                        handler.postDelayed(this::showNextQuestion, 1000); // đợi 1 giây rồi hiển thị câu tiếp theo
                    }
                }
        }
    } }
