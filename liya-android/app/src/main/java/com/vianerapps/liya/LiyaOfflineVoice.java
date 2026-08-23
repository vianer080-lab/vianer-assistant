package com.vianerapps.liya;

import android.content.Context;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;
import java.util.Locale;
import java.util.function.Consumer;

final class LiyaOfflineVoice implements RecognitionListener {
    private final Context context; private final Consumer<String> command; private final Consumer<String> state;
    private Model model; private SpeechService speech; private boolean active;
    LiyaOfflineVoice(Context context, Consumer<String> command, Consumer<String> state) { this.context=context; this.command=command; this.state=state; }
    void start() {
        state.accept("Подготавливаю локальный голос…");
        StorageService.unpack(context,"model-ru","liya-model-ru",loaded->{ model=loaded; try { speech=new SpeechService(new Recognizer(model,16000.0f),16000.0f); speech.startListening(this); state.accept("Скажите: Лия, включи голосовой режим"); } catch(Exception e){ state.accept("Не удалось запустить локальный голос"); } },error->state.accept("Не удалось загрузить русскую голосовую модель"));
    }
    void pause(boolean value){ if(speech!=null) speech.setPause(value); }
    void stop(){ if(speech!=null){speech.stop();speech.shutdown();speech=null;} if(model!=null){model.close();model=null;} }
    private void accept(String json){ try { String text=new JSONObject(json).optString("text","").toLowerCase(Locale.ROOT).trim(); if(text.isEmpty())return; if(text.contains("лия")&&text.contains("выключи голос")){active=false;state.accept("Голосовой режим выключен. Жду фразу включения.");return;} if(text.contains("лия")&&text.contains("включи голос")){active=true;state.accept("Голосовой режим включён. Слушаю вас.");return;} if(active)command.accept(text.replaceFirst("^лия[ ,]*","")); }catch(Exception ignored){} }
    @Override public void onResult(String h){accept(h);} @Override public void onFinalResult(String h){accept(h);} @Override public void onPartialResult(String h){} @Override public void onError(Exception e){state.accept("Ошибка локального голоса");} @Override public void onTimeout(){}
}
