package br.com.alura.ecomart.chatbot.infra.openai;

import com.theokanning.openai.completion.chat.ChatCompletionChunk;
import io.reactivex.Flowable;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

@Component
public class OpenAIClientMapper {
    public ResponseBodyEmitter fluxoStreamResposta(@NotNull final Flowable<ChatCompletionChunk>fluxoResposta) {
        var emitter = new ResponseBodyEmitter();
        fluxoResposta.subscribe(chunk -> {
            var token = chunk.getChoices().get(0).getMessage().getContent();
            if (token != null) {
                emitter.send(token);
            }
        }, emitter::completeWithError, emitter::complete);
        return emitter;
    }
}
