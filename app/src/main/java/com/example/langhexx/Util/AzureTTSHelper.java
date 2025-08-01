package com.example.langhexx.Util;

import android.util.Log;

import com.example.langhexx.BuildConfig;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

public class AzureTTSHelper {
    private static final String TAG = "AzureTTSHelper";
    private static final String AZURE_TTS_KEY = BuildConfig.AZURE_TTS_API_KEY;
    private static final String AZURE_TTS_ENDPOINT = "https://southeastasia.tts.speech.microsoft.com/cognitiveservices/v1";
    private static final OkHttpClient httpClient = new OkHttpClient();

    public interface TTSCallback {
        void onStart();
        void onDone(byte[] audioData);
        void onError(String errorMsg);
    }

    public static void synthesize(String text, TTSCallback callback) {
        if (callback != null) callback.onStart();

        String ssml = "<speak version='1.0' xml:lang='en-US'>" +
                "<voice name='en-US-JennyNeural'>" + escapeForSSML(text) + "</voice></speak>";

        Request request = new Request.Builder()
                .url(AZURE_TTS_ENDPOINT)
                .addHeader("Ocp-Apim-Subscription-Key", AZURE_TTS_KEY)
                .addHeader("Content-Type", "application/ssml+xml")
                .addHeader("X-Microsoft-OutputFormat", "audio-16khz-32kbitrate-mono-mp3")
                .addHeader("User-Agent", "LangHex-App")
                .post(RequestBody.create(ssml, MediaType.parse("application/ssml+xml")))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Azure TTS failed: " + e.getMessage());
                if (callback != null) callback.onError("Speech synthesis failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    if (callback != null) callback.onError("TTS failed: " + response.code());
                    return;
                }
                byte[] audioBytes = response.body().bytes();
                if (audioBytes.length == 0) {
                    if (callback != null) callback.onError("No audio data returned.");
                    return;
                }
                if (callback != null) callback.onDone(audioBytes);
            }
        });
    }

    private static String escapeForSSML(String input) {
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
