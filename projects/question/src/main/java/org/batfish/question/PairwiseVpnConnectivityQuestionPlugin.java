package org.batfish.question;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.service.AutoService;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.batfish.common.Answerer;
import org.batfish.common.plugin.IBatfish;
import org.batfish.common.plugin.Plugin;
import org.batfish.common.util.CommonUtil;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.IpsecVpn;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.questions.NodesSpecifier;
import org.batfish.datamodel.questions.Question;

@AutoService(Plugin.class)
public class PairwiseVpnConnectivityQuestionPlugin extends QuestionPlugin {

  public static class PairwiseVpnConnectivityAnswerElement implements AnswerElement {

    private SortedMap<String, SortedSet<String>> _connectedNeighbors;

    private SortedSet<String> _ipsecVpnNodes;

    private SortedMap<String, SortedSet<String>> _missingNeighbors;

    public PairwiseVpnConnectivityAnswerElement() {
      _connectedNeighbors = new TreeMap<>();
      _ipsecVpnNodes = new TreeSet<>();
      _missingNeighbors = new TreeMap<>();
    }

    public SortedMap<String, SortedSet<String>> getConnectedNeighbors() {
      return _connectedNeighbors;
    }

    public SortedSet<String> getIpsecVpnNodes() {
      return _ipsecVpnNodes;
    }

    public SortedMap<String, SortedSet<String>> getMissingNeighbors() {
      return _missingNeighbors;
    }

    public void setConnectedNeighbors(SortedMap<String, SortedSet<String>> connectedNeighbors) {
      _connectedNeighbors = connectedNeighbors;
    }

    public void setIpsecVpnNodes(SortedSet<String> ipsecVpnNodes) {
      _ipsecVpnNodes = ipsecVpnNodes;
    }

    public void setMissingNeighbors(SortedMap<String, SortedSet<String>> missingNeighbors) {
      _missingNeighbors = missingNeighbors;
    }
  }

  public static class PairwiseVpnConnectivityAnswerer extends Answerer {

    public PairwiseVpnConnectivityAnswerer(Question question, IBatfish batfish) {
      super(question, batfish);
    }

    @Override
    public AnswerElement answer() {
      PairwiseVpnConnectivityQuestion question = (PairwiseVpnConnectivityQuestion) _question;

      PairwiseVpnConnectivityAnswerElement answerElement =
          new PairwiseVpnConnectivityAnswerElement();

      Map<String, Configuration> configurations = _batfish.loadConfigurations();
      Set<String> includeNodes1 = question.getNode1Regex().getMatchingNodes(configurations);
      Set<String> includeNodes2 = question.getNode2Regex().getMatchingNodes(configurations);

      CommonUtil.initRemoteIpsecVpns(configurations);
      Set<String> ipsecVpnNodes = answerElement.getIpsecVpnNodes();
      Set<String> node2RegexNodes = new HashSet<>();

      for (Configuration c : configurations.values()) {
        String hostname = c.getHostname();
        if (!c.getIpsecVpns().isEmpty()) {
          if (includeNodes1.contains(hostname)) {
            ipsecVpnNodes.add(c.getHostname());
          }
          if (includeNodes2.contains(hostname)) {
            node2RegexNodes.add(hostname);
          }
        }
      }
      for (Configuration c : configurations.values()) {
        String hostname = c.getHostname();
        if (!ipsecVpnNodes.contains(hostname)) {
          continue;
        }
        SortedSet<String> currentNeighbors = new TreeSet<>();
        if (!c.getIpsecVpns().isEmpty()) {
          for (IpsecVpn ipsecVpn : c.getIpsecVpns().values()) {
            if (ipsecVpn.getRemoteIpsecVpn() != null) {
              for (IpsecVpn remoteIpsecVpn : ipsecVpn.getCandidateRemoteIpsecVpns()) {
                String remoteHost = remoteIpsecVpn.getOwner().getHostname();
                if (node2RegexNodes.contains(remoteHost)) {
                  currentNeighbors.add(remoteHost);
                }
              }
            }
          }
          SortedSet<String> missingNeighbors = new TreeSet<>();
          missingNeighbors.addAll(node2RegexNodes);
          missingNeighbors.removeAll(currentNeighbors);
          missingNeighbors.remove(hostname);
          answerElement.getConnectedNeighbors().put(hostname, currentNeighbors);
          answerElement.getMissingNeighbors().put(hostname, missingNeighbors);
        }
      }

      return answerElement;
    }
  }

  // <question_page_comment>

  /**
   * Checks if VPN connectivity between pairs of nodes is correctly configured.
   *
   * <p>Details coming on what it means to be correctly configured.
   *
   * @type PairwiseVpnConnectivity multifile
   * @param node1Regex Regular expression to match the nodes names for one end of the sessions.
   *     Default is '.*' (all nodes).
   * @param node2Regex Regular expression to match the nodes names for the other end of the
   *     sessions. Default is '.*' (all nodes).
   * @example bf_answer("PairwiseVpnConnectivity", node1Regex="as1.*", node2Regex="as2.*") Checks
   *     pairwise VPN connectivity between nodes that start with as1 and those that start with as2.
   */
  public static class PairwiseVpnConnectivityQuestion extends Question {

    private static final String PROP_NODE1_REGEX = "node1Regex";

    private static final String PROP_NODE2_REGEX = "node2Regex";

    private NodesSpecifier _node1Regex;

    private NodesSpecifier _node2Regex;

    public PairwiseVpnConnectivityQuestion() {
      _node1Regex = NodesSpecifier.ALL;
      _node2Regex = NodesSpecifier.ALL;
    }

    @Override
    public boolean getDataPlane() {
      return false;
    }

    @Override
    public String getName() {
      return "pairwisevpnconnectivity";
    }

    @JsonProperty(PROP_NODE1_REGEX)
    public NodesSpecifier getNode1Regex() {
      return _node1Regex;
    }

    @JsonProperty(PROP_NODE2_REGEX)
    public NodesSpecifier getNode2Regex() {
      return _node2Regex;
    }

    @JsonProperty(PROP_NODE1_REGEX)
    public void setNode1Regex(NodesSpecifier regex) {
      _node1Regex = regex;
    }

    @JsonProperty(PROP_NODE2_REGEX)
    public void setNode2Regex(NodesSpecifier regex) {
      _node2Regex = regex;
    }
  }

  @Override
  protected Answerer createAnswerer(Question question, IBatfish batfish) {
    return new PairwiseVpnConnectivityAnswerer(question, batfish);
  }

  @Override
  protected Question createQuestion() {
    return new PairwiseVpnConnectivityQuestion();
  }
}
