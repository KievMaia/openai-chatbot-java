package br.com.alura.ecomart.chatbot.domain.service;

import br.com.alura.ecomart.chatbot.infra.openai.DadosRequisicaoChatCompletion;
import br.com.alura.ecomart.chatbot.infra.openai.OpenAIClient;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChatbotService {
    private final OpenAIClient client;

    public ChatbotService(OpenAIClient openAIClient) {this.client = openAIClient;}

    public String responderPergunta(@NotNull final String pergunta) {
        var promptSistema =
                "Você é um chatbot de atendimento a clientes de um ecommerce e deve responder apenas perguntas " +
                        "relacionadas com o ecommerce";
        return client.enviarRequisicaoChatCompletion(new DadosRequisicaoChatCompletion(promptSistema, pergunta));
    }
}
