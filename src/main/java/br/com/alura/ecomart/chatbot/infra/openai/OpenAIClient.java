package br.com.alura.ecomart.chatbot.infra.openai;

import br.com.alura.ecomart.chatbot.domain.DadosCalculoFrete;
import br.com.alura.ecomart.chatbot.domain.service.CalculadorDeFrete;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.OpenAiResponse;
import com.theokanning.openai.completion.chat.*;
import com.theokanning.openai.messages.Message;
import com.theokanning.openai.messages.MessageRequest;
import com.theokanning.openai.runs.Run;
import com.theokanning.openai.runs.RunCreateRequest;
import com.theokanning.openai.runs.SubmitToolOutputRequestItem;
import com.theokanning.openai.runs.SubmitToolOutputsRequest;
import com.theokanning.openai.service.FunctionExecutor;
import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.threads.ThreadRequest;
import io.reactivex.Flowable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

@Component
public class OpenAIClient {

    private final String assistantId;
    private String threadId;
    private final OpenAiService service;

    private final CalculadorDeFrete calculadorDeFrete;

    public OpenAIClient(final @Value("${app.openai.api.key}") String apiKey,
                        final @Value("${app.openai.assistant.id}") String assistantId,
                        final CalculadorDeFrete calculadorDeFrete) {
        this.service = new OpenAiService(apiKey, Duration.ofSeconds(60));
        this.assistantId = assistantId;
        this.calculadorDeFrete = calculadorDeFrete;
    }

    public String enviarRequisicaoChatCompletion(DadosRequisicaoChatCompletion dados) {
        var messageRequest = MessageRequest
                .builder()
                .role(ChatMessageRole.USER.value())
                .content(dados.promptUsuario())
                .build();

        if (this.threadId == null) {
            var threadRequest = ThreadRequest
                    .builder()
                    .messages(Collections.singletonList(messageRequest))
                    .build();

            var thread = service.createThread(threadRequest);
            this.threadId = thread.getId();
        } else {
            service.createMessage(this.threadId, messageRequest);
        }

        var runRequest = RunCreateRequest
                .builder()
                .assistantId(assistantId)
                .build();
        var run = service.createRun(threadId, runRequest);

        var concluido = false;
        var precisaChamarFuncao = false;
        try {
            while (!concluido && !precisaChamarFuncao) {
                Thread.sleep(1000 * 10);
                run = service.retrieveRun(threadId, run.getId());
                concluido = run.getStatus().equalsIgnoreCase("completed");
                precisaChamarFuncao = run.getRequiredAction() != null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (precisaChamarFuncao) {
            var precoDoFrete = this.chamarFuncao(run);
            var submitRequest = SubmitToolOutputsRequest
                    .builder()
                    .toolOutputs(List.of(
                            new SubmitToolOutputRequestItem(
                                    run
                                            .getRequiredAction()
                                            .getSubmitToolOutputs()
                                            .getToolCalls()
                                            .get(0)
                                            .getId(),
                                    precoDoFrete)
                    ))
                    .build();
            service.submitToolOutputs(threadId, run.getId(), submitRequest);

            try {
                while (!concluido) {
                    Thread.sleep(1000 * 10);
                    run = service.retrieveRun(threadId, run.getId());
                    concluido = run.getStatus().equalsIgnoreCase("completed");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        var mensagens = service.listMessages(threadId);
        return mensagens
                .getData()
                .stream()
                .max(Comparator.comparingInt(Message::getCreatedAt))
                .get()
                .getContent()
                .get(0)
                .getText()
                .getValue()
                .replaceAll("【.*?】", "");
    }

    public List<String> carregarHistoricoDeMensagens() {
        var mensagens = new ArrayList<String>();

        if (threadId != null) {
            mensagens.addAll(service.listMessages(this.threadId)
                                     .getData()
                                     .stream()
                                     .sorted(Comparator.comparingInt(Message::getCreatedAt))
                                     .map(m -> m.getContent().get(0).getText().getValue())
                                     .toList());
        }
        return mensagens;
    }

    public void apagarThread() {
        if (this.threadId != null) {
            service.deleteThread(threadId);
            this.threadId = null;
        }
    }

    private String chamarFuncao(Run run) {
        try {
            var funcao = run.getRequiredAction().getSubmitToolOutputs().getToolCalls().get(0).getFunction();
            var funcaoCalcularFrete = ChatFunction.builder()
                    .name("calcularFrete")
                    .executor(DadosCalculoFrete.class, calculadorDeFrete::calcular)
                    .build();

            var executorDeFuncoes = new FunctionExecutor(Collections.singletonList(funcaoCalcularFrete));
            var functionCall =
                    new ChatFunctionCall(funcao.getName(), new ObjectMapper().readTree(funcao.getArguments()));
            return executorDeFuncoes.execute(functionCall).toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}