package dev.devce.rocketnautics.content.sputnik.node;

import dev.devce.rocketnautics.content.sputnik.model.SputnikNode;
import dev.devce.rocketnautics.content.sputnik.model.SputnikPin;

import java.util.List;

public interface INodeHandler {
    String getTypeId();
    String getTitle();
    String getCategory();
    int getHeaderColor();
    List<SputnikPin> createInputs();
    List<SputnikPin> createOutputs();
    void execute(SputnikNode node, NodeExecutionContext context);
}
