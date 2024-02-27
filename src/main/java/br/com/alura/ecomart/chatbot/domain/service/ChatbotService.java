package br.com.alura.ecomart.chatbot.domain.service;

import br.com.alura.ecomart.chatbot.infra.openai.DadosRequisicaoChatCompletion;
import br.com.alura.ecomart.chatbot.infra.openai.OpenAIClient;
import br.com.alura.ecomart.chatbot.infra.openai.OpenAIClientMapper;
import io.reactivex.Flowable;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

@Service
public class ChatbotService {
    private final OpenAIClient client;

    private final OpenAIClientMapper mapper;

    public ChatbotService(OpenAIClient openAIClient, OpenAIClientMapper mapper) {this.client = openAIClient;
        this.mapper = mapper;
    }

    public ResponseBodyEmitter responderPergunta(@NotNull final String pergunta) {
        var promptSistema =
                "Você é um chatbot de atendimento a clientes de um ecommerce e deve responder apenas perguntas " +
                        "relacionadas com o ecommerce";
        final var chatCompletionChunkFlowable =
                client.enviarRequisicaoChatCompletion(new DadosRequisicaoChatCompletion(promptSistema, pergunta));
        return mapper.fluxoStreamResposta(chatCompletionChunkFlowable);
    }
}
