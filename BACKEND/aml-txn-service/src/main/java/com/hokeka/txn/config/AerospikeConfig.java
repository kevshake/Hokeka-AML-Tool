package com.hokeka.txn.config;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Host;
import com.aerospike.client.policy.ClientPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class AerospikeConfig {

  @Value("${aml.aerospike.hosts:aml-aerospike:3000}")
  private String hostsCsv;

  @Bean(destroyMethod = "close")
  public AerospikeClient aerospikeClient() {
    List<Host> hosts = new ArrayList<>();
    for (String entry : hostsCsv.split(",")) {
      String[] parts = entry.trim().split(":");
      hosts.add(new Host(parts[0], Integer.parseInt(parts[1])));
    }
    ClientPolicy policy = new ClientPolicy();
    return new AerospikeClient(policy, hosts.toArray(new Host[0]));
  }
}
