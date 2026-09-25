package com.posgateway.aml.service.jev;

import com.posgateway.aml.entity.jev.JevEngineSetting;
import com.posgateway.aml.repository.jev.JevEngineSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class JevEngineConfigService {

    private final JevEngineSettingRepository repository;

    public JevEngineConfigService(JevEngineSettingRepository repository) {
        this.repository = repository;
    }

    public boolean isEngineEnabled(JevEngineType engine) {
        return find(engine).map(JevEngineSetting::isEnabled).orElse(true);
    }

    public boolean isAdvisoryOnly(JevEngineType engine) {
        return find(engine).map(JevEngineSetting::isAdvisoryOnly).orElse(true);
    }

    public String promptVersion(JevEngineType engine) {
        return find(engine).map(JevEngineSetting::getPromptVersion).orElse("v1");
    }

    public List<JevEngineSetting> listAll() {
        return repository.findAll();
    }

    @Transactional
    public JevEngineSetting update(String engineCode, Boolean enabled, Boolean advisoryOnly,
                                   String promptVersion, String updatedBy) {
        JevEngineSetting setting = repository.findById(engineCode)
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

    private Optional<JevEngineSetting> find(JevEngineType engine) {
        return repository.findById(engine.name());
    }
}
