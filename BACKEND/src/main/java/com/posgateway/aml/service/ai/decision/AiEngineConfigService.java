package com.posgateway.aml.service.ai.decision;

import com.posgateway.aml.entity.ai.AiEngineSetting;
import com.posgateway.aml.repository.ai.AiEngineSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class AiEngineConfigService {

    private final AiEngineSettingRepository repository;

    public AiEngineConfigService(AiEngineSettingRepository repository) {
        this.repository = repository;
    }

    public boolean isEngineEnabled(AiEngineType engine) {
        return find(engine).map(AiEngineSetting::isEnabled).orElse(true);
    }

    public boolean isAdvisoryOnly(AiEngineType engine) {
        return find(engine).map(AiEngineSetting::isAdvisoryOnly).orElse(true);
    }

    public String promptVersion(AiEngineType engine) {
        return find(engine).map(AiEngineSetting::getPromptVersion).orElse("v1");
    }

    public List<AiEngineSetting> listAll() {
        return repository.findAll();
    }

    @Transactional
    public AiEngineSetting update(String engineCode, Boolean enabled, Boolean advisoryOnly,
                                   String promptVersion, String updatedBy) {
        AiEngineSetting setting = repository.findById(engineCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown engine: " + engineCode));
        if (enabled != null) {
            setting.setEnabled(enabled);
        }
        if (advisoryOnly != null) {
            setting.setAdvisoryOnly(advisoryOnly);
        }
        if (promptVersion != null && !promptVersion.isBlank()) {
            setting.setPromptVersion(promptVersion);
        }
        setting.setUpdatedAt(Instant.now());
        setting.setUpdatedBy(updatedBy);
        return repository.save(setting);
    }

    private Optional<AiEngineSetting> find(AiEngineType engine) {
        return repository.findById(engine.name());
    }
}
