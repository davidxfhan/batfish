package org.batfish.bdp;

import static org.batfish.datamodel.matchers.AbstractRouteMatchers.hasPrefix;
import static org.batfish.datamodel.matchers.AbstractRouteMatchers.hasProtocol;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Collections;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.batfish.common.BatfishLogger;
import org.batfish.common.util.CommonUtil;
import org.batfish.datamodel.AbstractRoute;
import org.batfish.datamodel.AsPath;
import org.batfish.datamodel.BgpAdvertisement;
import org.batfish.datamodel.BgpAdvertisement.BgpAdvertisementType;
import org.batfish.datamodel.BgpNeighbor;
import org.batfish.datamodel.BgpProcess;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.ConfigurationFormat;
import org.batfish.datamodel.Interface;
import org.batfish.datamodel.InterfaceAddress;
import org.batfish.datamodel.Ip;
import org.batfish.datamodel.NetworkFactory;
import org.batfish.datamodel.OriginType;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.RoutingProtocol;
import org.batfish.datamodel.Topology;
import org.batfish.datamodel.Vrf;
import org.batfish.datamodel.answers.BdpAnswerElement;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.routing_policy.expr.Disjunction;
import org.batfish.datamodel.routing_policy.expr.MatchProtocol;
import org.batfish.datamodel.routing_policy.statement.If;
import org.batfish.datamodel.routing_policy.statement.Statements;
import org.junit.Before;
import org.junit.Test;

public class RouteReflectionTest {

  private static Prefix AS1_PREFIX = Prefix.parse("1.0.0.0/8");

  private static Prefix AS3_PREFIX = Prefix.parse("3.0.0.0/8");

  private static String EDGE1_NAME = "edge1";

  private static String EDGE2_NAME = "edge2";

  private static String RR_NAME = "rr";

  private static String RR1_NAME = "rr1";

  private static String RR2_NAME = "rr2";

  private static void assertIbgpRoute(
      SortedMap<String, SortedMap<String, SortedSet<AbstractRoute>>> routesByNode,
      String hostname,
      Prefix prefix) {
    assertThat(routesByNode, hasKey(hostname));
    SortedMap<String, SortedSet<AbstractRoute>> routesByVrf = routesByNode.get(hostname);
    assertThat(routesByVrf, hasKey(Configuration.DEFAULT_VRF_NAME));
    SortedSet<AbstractRoute> routes = routesByVrf.get(Configuration.DEFAULT_VRF_NAME);
    assertThat(routes, hasItem(hasPrefix(prefix)));
    AbstractRoute route =
        routes.stream().filter(r -> r.getNetwork().equals(prefix)).findAny().get();
    assertThat(route, hasProtocol(RoutingProtocol.IBGP));
  }

  private static void assertNoRoute(
      SortedMap<String, SortedMap<String, SortedSet<AbstractRoute>>> routesByNode,
      String hostname,
      Prefix prefix) {
    assertThat(routesByNode, hasKey(hostname));
    SortedMap<String, SortedSet<AbstractRoute>> routesByVrf = routesByNode.get(hostname);
    assertThat(routesByVrf, hasKey(Configuration.DEFAULT_VRF_NAME));
    SortedSet<AbstractRoute> routes = routesByVrf.get(Configuration.DEFAULT_VRF_NAME);
    assertThat(routes, not(hasItem(hasPrefix(prefix))));
  }

  private BgpAdvertisement.Builder _ab;

  private Configuration.Builder _cb;

  private RoutingPolicy.Builder _defaultExportPolicyBuilder;

  private int _edgePrefixLength;

  private Interface.Builder _ib;

  private BgpNeighbor.Builder _nb;

  private NetworkFactory _nf;

  private RoutingPolicy.Builder _nullExportPolicyBuilder;

  private BgpProcess.Builder _pb;

  private Vrf.Builder _vb;

  private SortedMap<String, SortedMap<String, SortedSet<AbstractRoute>>> generateRoutesOneReflector(
      boolean edge1RouteReflectorClient, boolean edge2RouteReflectorClient) {
    Ip as1PeeringIp = new Ip("10.12.11.1");
    Ip edge1EbgpIfaceIp = new Ip("10.12.11.2");
    Ip edge1IbgpIfaceIp = new Ip("10.1.12.1");
    Ip edge1LoopbackIp = new Ip("2.0.0.1");
    Ip rrEdge1IfaceIp = new Ip("10.1.12.2");
    Ip rrEdge2IfaceIp = new Ip("10.1.23.2");
    Ip rrLoopbackIp = new Ip("2.0.0.2");
    Ip as3PeeringIp = new Ip("10.23.31.3");
    Ip edge2EbgpIfaceIp = new Ip("10.23.31.2");
    Ip edge2IbgpIfaceIp = new Ip("10.1.23.3");
    Ip edge2LoopbackIp = new Ip("2.0.0.3");

    Configuration edge1 = _cb.setHostname(EDGE1_NAME).build();
    Vrf vEdge1 = _vb.setOwner(edge1).build();
    _ib.setOwner(edge1).setVrf(vEdge1);
    _ib.setAddress(new InterfaceAddress(edge1EbgpIfaceIp, _edgePrefixLength)).build();
    _ib.setAddress(new InterfaceAddress(edge1LoopbackIp, Prefix.MAX_PREFIX_LENGTH)).build();
    _ib.setAddress(new InterfaceAddress(edge1IbgpIfaceIp, _edgePrefixLength)).build();
    BgpProcess edge1Proc = _pb.setRouterId(edge1LoopbackIp).setVrf(vEdge1).build();
    RoutingPolicy edge1EbgpExportPolicy = _nullExportPolicyBuilder.setOwner(edge1).build();
    _nb.setOwner(edge1)
        .setVrf(vEdge1)
        .setBgpProcess(edge1Proc)
        .setClusterId(edge1LoopbackIp.asLong())
        .setRemoteAs(1)
        .setLocalIp(edge1EbgpIfaceIp)
        .setPeerAddress(as1PeeringIp)
        .setExportPolicy(edge1EbgpExportPolicy.getName())
        .build();
    RoutingPolicy edge1IbgpExportPolicy = _defaultExportPolicyBuilder.setOwner(edge1).build();
    _nb.setRemoteAs(2)
        .setLocalIp(edge1LoopbackIp)
        .setPeerAddress(rrLoopbackIp)
        .setExportPolicy(edge1IbgpExportPolicy.getName())
        .build();

    Configuration rr = _cb.setHostname(RR_NAME).build();
    Vrf vRr = _vb.setOwner(rr).build();
    _ib.setOwner(rr).setVrf(vRr);
    _ib.setAddress(new InterfaceAddress(rrEdge1IfaceIp, _edgePrefixLength)).build();
    _ib.setAddress(new InterfaceAddress(rrLoopbackIp, Prefix.MAX_PREFIX_LENGTH)).build();
    _ib.setAddress(new InterfaceAddress(rrEdge2IfaceIp, _edgePrefixLength)).build();
    BgpProcess rrProc = _pb.setRouterId(rrLoopbackIp).setVrf(vRr).build();
    RoutingPolicy rrExportPolicy = _defaultExportPolicyBuilder.setOwner(rr).build();
    _nb.setOwner(rr)
        .setVrf(vRr)
        .setBgpProcess(rrProc)
        .setClusterId(rrLoopbackIp.asLong())
        .setRemoteAs(2)
        .setLocalIp(rrLoopbackIp)
        .setExportPolicy(rrExportPolicy.getName());
    if (edge1RouteReflectorClient) {
      _nb.setRouteReflectorClient(true);
    }
    _nb.setPeerAddress(edge1LoopbackIp).build();
    _nb.setRouteReflectorClient(false);
    if (edge2RouteReflectorClient) {
      _nb.setRouteReflectorClient(true);
    }
    _nb.setPeerAddress(edge2LoopbackIp).build();
    _nb.setRouteReflectorClient(false);

    Configuration edge2 = _cb.setHostname(EDGE2_NAME).build();
    Vrf vEdge2 = _vb.setOwner(edge2).build();
    _ib.setOwner(edge2).setVrf(vEdge2);
    _ib.setAddress(new InterfaceAddress(edge2EbgpIfaceIp, _edgePrefixLength)).build();
    _ib.setAddress(new InterfaceAddress(edge2LoopbackIp, Prefix.MAX_PREFIX_LENGTH)).build();
    _ib.setAddress(new InterfaceAddress(edge2IbgpIfaceIp, _edgePrefixLength)).build();
    BgpProcess edge2Proc = _pb.setRouterId(edge2LoopbackIp).setVrf(vEdge2).build();
    RoutingPolicy edge2EbgpExportPolicy = _nullExportPolicyBuilder.setOwner(edge2).build();
    _nb.setOwner(edge2)
        .setVrf(vEdge2)
        .setBgpProcess(edge2Proc)
        .setClusterId(edge2LoopbackIp.asLong())
        .setRemoteAs(3)
        .setLocalIp(edge2EbgpIfaceIp)
        .setPeerAddress(as3PeeringIp)
        .setExportPolicy(edge2EbgpExportPolicy.getName())
        .build();
    RoutingPolicy edge2IbgpExportPolicy = _defaultExportPolicyBuilder.setOwner(edge2).build();
    _nb.setRemoteAs(2)
        .setLocalIp(edge2LoopbackIp)
        .setPeerAddress(rrLoopbackIp)
        .setExportPolicy(edge2IbgpExportPolicy.getName())
        .build();

    SortedMap<String, Configuration> configurations =
        new ImmutableSortedMap.Builder<String, Configuration>(String::compareTo)
            .put(edge1.getName(), edge1)
            .put(rr.getName(), rr)
            .put(edge2.getName(), edge2)
            .build();
    BdpEngine engine =
        new BdpEngine(
            new TestBdpSettings(),
            new BatfishLogger(BatfishLogger.LEVELSTR_OUTPUT, false),
            (s, i) -> new AtomicInteger());
    Topology topology = CommonUtil.synthesizeTopology(configurations);
    BdpDataPlane dp =
        engine.computeDataPlane(
            false,
            configurations,
            topology,
            ImmutableSet.of(
                _ab.setAsPath(AsPath.ofSingletonAsSets(1))
                    .setDstIp(edge1EbgpIfaceIp)
                    .setDstNode(edge1.getName())
                    .setNetwork(AS1_PREFIX)
                    .setNextHopIp(as1PeeringIp)
                    .setOriginatorIp(as1PeeringIp)
                    .setSrcIp(as1PeeringIp)
                    .setSrcNode("as1Edge")
                    .build(),
                _ab.setAsPath(AsPath.ofSingletonAsSets(3))
                    .setDstIp(edge2EbgpIfaceIp)
                    .setDstNode(edge2.getName())
                    .setNetwork(AS3_PREFIX)
                    .setNextHopIp(as3PeeringIp)
                    .setOriginatorIp(as3PeeringIp)
                    .setSrcIp(as3PeeringIp)
                    .setSrcNode("as3Edge")
                    .build()),
            Collections.emptySet(),
            new BdpAnswerElement());
    return engine.getRoutes(dp);
  }

  private SortedMap<String, SortedMap<String, SortedSet<AbstractRoute>>>
      generateRoutesTwoReflectors(boolean useSameClusterIds) {
    Ip as1PeeringIp = new Ip("10.12.11.1");
    Ip edge1EbgpIfaceIp = new Ip("10.12.11.2");
    Ip edge1IbgpIfaceIp = new Ip("10.1.12.1");
    Ip edge1LoopbackIp = new Ip("2.0.0.1");
    Ip rr1Edge1IfaceIp = new Ip("10.1.12.2");
    Ip rr1Rr2IfaceIp = new Ip("10.1.23.2");
    Ip rr1LoopbackIp = new Ip("2.0.0.2");
    Ip rr2IbgpIfaceIp = new Ip("10.1.23.3");
    Ip rr2LoopbackIp = new Ip("2.0.0.3");

    Configuration edge1 = _cb.setHostname(EDGE1_NAME).build();
    Vrf vEdge1 = _vb.setOwner(edge1).build();
    _ib.setOwner(edge1).setVrf(vEdge1);
    _ib.setAddress(new InterfaceAddress(edge1EbgpIfaceIp, _edgePrefixLength)).build();
    _ib.setAddress(new InterfaceAddress(edge1LoopbackIp, Prefix.MAX_PREFIX_LENGTH)).build();
    _ib.setAddress(new InterfaceAddress(edge1IbgpIfaceIp, _edgePrefixLength)).build();
    BgpProcess edge1Proc = _pb.setRouterId(edge1LoopbackIp).setVrf(vEdge1).build();
    RoutingPolicy edge1EbgpExportPolicy = _nullExportPolicyBuilder.setOwner(edge1).build();
    _nb.setOwner(edge1)
        .setVrf(vEdge1)
        .setBgpProcess(edge1Proc)
        .setClusterId(edge1LoopbackIp.asLong())
        .setRemoteAs(1)
        .setLocalIp(edge1EbgpIfaceIp)
        .setPeerAddress(as1PeeringIp)
        .setExportPolicy(edge1EbgpExportPolicy.getName())
        .build();
    RoutingPolicy edge1IbgpExportPolicy = _defaultExportPolicyBuilder.setOwner(edge1).build();
    _nb.setRemoteAs(2)
        .setLocalIp(edge1LoopbackIp)
        .setPeerAddress(rr1LoopbackIp)
        .setExportPolicy(edge1IbgpExportPolicy.getName())
        .build();

    Configuration rr1 = _cb.setHostname(RR1_NAME).build();
    Vrf vRr1 = _vb.setOwner(rr1).build();
    _ib.setOwner(rr1).setVrf(vRr1);
    _ib.setAddress(new InterfaceAddress(rr1Edge1IfaceIp, _edgePrefixLength)).build();
    _ib.setAddress(new InterfaceAddress(rr1LoopbackIp, Prefix.MAX_PREFIX_LENGTH)).build();
    _ib.setAddress(new InterfaceAddress(rr1Rr2IfaceIp, _edgePrefixLength)).build();
    BgpProcess rr1Proc = _pb.setRouterId(rr1LoopbackIp).setVrf(vRr1).build();
    RoutingPolicy rr1ExportPolicy = _defaultExportPolicyBuilder.setOwner(rr1).build();
    _nb.setOwner(rr1)
        .setVrf(vRr1)
        .setBgpProcess(rr1Proc)
        .setClusterId(rr1LoopbackIp.asLong())
        .setRemoteAs(2)
        .setLocalIp(rr1LoopbackIp)
        .setExportPolicy(rr1ExportPolicy.getName())
        .setRouteReflectorClient(true)
        .setPeerAddress(edge1LoopbackIp)
        .build();
    _nb.setRouteReflectorClient(false).setPeerAddress(rr2LoopbackIp).build();

    Configuration rr2 = _cb.setHostname(RR2_NAME).build();
    Vrf vRr2 = _vb.setOwner(rr2).build();
    _ib.setOwner(rr2).setVrf(vRr2);
    _ib.setAddress(new InterfaceAddress(rr2LoopbackIp, Prefix.MAX_PREFIX_LENGTH)).build();
    _ib.setAddress(new InterfaceAddress(rr2IbgpIfaceIp, _edgePrefixLength)).build();
    BgpProcess rr2Proc = _pb.setRouterId(rr2LoopbackIp).setVrf(vRr2).build();
    RoutingPolicy edge2IbgpExportPolicy = _defaultExportPolicyBuilder.setOwner(rr2).build();

    Ip rr2ClusterIdForRr1 = useSameClusterIds ? rr1LoopbackIp : rr2LoopbackIp;
    _nb.setOwner(rr2)
        .setVrf(vRr2)
        .setBgpProcess(rr2Proc)
        .setClusterId(rr2ClusterIdForRr1.asLong())
        .setLocalIp(rr2LoopbackIp)
        .setPeerAddress(rr1LoopbackIp)
        .setRouteReflectorClient(true)
        .setExportPolicy(edge2IbgpExportPolicy.getName())
        .build();

    SortedMap<String, Configuration> configurations =
        new ImmutableSortedMap.Builder<String, Configuration>(String::compareTo)
            .put(edge1.getName(), edge1)
            .put(rr1.getName(), rr1)
            .put(rr2.getName(), rr2)
            .build();
    BdpEngine engine =
        new BdpEngine(
            new TestBdpSettings(),
            new BatfishLogger(BatfishLogger.LEVELSTR_OUTPUT, false),
            (s, i) -> new AtomicInteger());
    Topology topology = CommonUtil.synthesizeTopology(configurations);
    BdpDataPlane dp =
        engine.computeDataPlane(
            false,
            configurations,
            topology,
            ImmutableSet.of(
                _ab.setAsPath(AsPath.ofSingletonAsSets(1))
                    .setDstIp(edge1EbgpIfaceIp)
                    .setDstNode(edge1.getName())
                    .setNetwork(AS1_PREFIX)
                    .setNextHopIp(as1PeeringIp)
                    .setOriginatorIp(as1PeeringIp)
                    .setSrcIp(as1PeeringIp)
                    .setSrcNode("as1Edge")
                    .build()),
            Collections.emptySet(),
            new BdpAnswerElement());
    return engine.getRoutes(dp);
  }

  @Before
  public void setup() {
    _ab =
        new BgpAdvertisement.Builder()
            .setClusterList(ImmutableSortedSet.of())
            .setCommunities(ImmutableSortedSet.of())
            .setDstVrf(Configuration.DEFAULT_VRF_NAME)
            .setOriginType(OriginType.INCOMPLETE)
            .setSrcProtocol(RoutingProtocol.AGGREGATE)
            .setSrcVrf(Configuration.DEFAULT_VRF_NAME)
            .setType(BgpAdvertisementType.EBGP_SENT);
    _nf = new NetworkFactory();
    _cb = _nf.configurationBuilder().setConfigurationFormat(ConfigurationFormat.CISCO_IOS);
    _ib = _nf.interfaceBuilder().setActive(true);
    _nb = _nf.bgpNeighborBuilder().setLocalAs(2);
    _pb = _nf.bgpProcessBuilder();
    _vb = _nf.vrfBuilder().setName(Configuration.DEFAULT_VRF_NAME);
    _edgePrefixLength = 24;
    If acceptIffBgp = new If();
    Disjunction guard = new Disjunction();
    guard.setDisjuncts(
        ImmutableList.of(
            new MatchProtocol(RoutingProtocol.BGP), new MatchProtocol(RoutingProtocol.IBGP)));
    acceptIffBgp.setGuard(guard);
    acceptIffBgp.setTrueStatements(ImmutableList.of(Statements.ExitAccept.toStaticStatement()));
    acceptIffBgp.setFalseStatements(ImmutableList.of(Statements.ExitReject.toStaticStatement()));
    _defaultExportPolicyBuilder =
        _nf.routingPolicyBuilder().setStatements(ImmutableList.of(acceptIffBgp));
    _nullExportPolicyBuilder =
        _nf.routingPolicyBuilder()
            .setStatements(ImmutableList.of(Statements.ExitReject.toStaticStatement()));
  }

  /*
   * AS1 |                                            AS2
   *       edge1(client of rr, CID: rr1-loopback) <=> rr1(client of rr2, CID: rr2-loopback) <=> rr2
   */
  @Test
  public void testAcceptDifferentCluster() {
    SortedMap<String, SortedMap<String, SortedSet<AbstractRoute>>> routes =
        generateRoutesTwoReflectors(false);

    assertIbgpRoute(routes, RR1_NAME, AS1_PREFIX);
    assertIbgpRoute(routes, RR2_NAME, AS1_PREFIX);
  }

  /*
   * AS1 |           AS2                      | AS3
   *       edge1 <=> rr(no clients) <=> edge2
   */
  @Test
  public void testNoRouteReflection() {
    SortedMap<String, SortedMap<String, SortedSet<AbstractRoute>>> routes =
        generateRoutesOneReflector(false, false);

    assertNoRoute(routes, EDGE1_NAME, AS3_PREFIX);
    assertIbgpRoute(routes, RR_NAME, AS1_PREFIX);
    assertIbgpRoute(routes, RR_NAME, AS3_PREFIX);
    assertNoRoute(routes, EDGE2_NAME, AS1_PREFIX);
  }

  /*
   * AS1 |                                            AS2
   *       edge1(client of rr, CID: rr1-loopback) <=> rr1(client of rr2, CID: rr1-loopback) <=> rr2
   */
  @Test
  public void testRejectSameCluster() {
    SortedMap<String, SortedMap<String, SortedSet<AbstractRoute>>> routes =
        generateRoutesTwoReflectors(true);

    assertIbgpRoute(routes, RR1_NAME, AS1_PREFIX);
    assertNoRoute(routes, RR2_NAME, AS1_PREFIX);
  }

  /*
   * AS1 |                   AS2          | AS3
   *       edge1(client) <=> rr <=> edge2
   */
  @Test
  public void testSingleReflectorOneClient() {
    SortedMap<String, SortedMap<String, SortedSet<AbstractRoute>>> routes =
        generateRoutesOneReflector(true, false);

    assertIbgpRoute(routes, EDGE1_NAME, AS3_PREFIX);
    assertIbgpRoute(routes, RR_NAME, AS1_PREFIX);
    assertIbgpRoute(routes, RR_NAME, AS3_PREFIX);
    assertIbgpRoute(routes, EDGE2_NAME, AS1_PREFIX);
  }

  /*
   * AS1 |                  AS2                   | AS3
   *       edge1(client) <=> rr <=> (client)edge2
   */
  @Test
  public void testSingleReflectorTwoClients() {
    SortedMap<String, SortedMap<String, SortedSet<AbstractRoute>>> routes =
        generateRoutesOneReflector(true, true);

    assertIbgpRoute(routes, EDGE1_NAME, AS3_PREFIX);
    assertIbgpRoute(routes, RR_NAME, AS1_PREFIX);
    assertIbgpRoute(routes, RR_NAME, AS3_PREFIX);
    assertIbgpRoute(routes, EDGE2_NAME, AS1_PREFIX);
  }
}
