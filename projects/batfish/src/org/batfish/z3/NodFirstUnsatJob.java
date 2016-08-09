package org.batfish.z3;

import org.batfish.common.BatfishException;
import org.batfish.job.BatfishJob;
import org.batfish.main.Settings;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Fixedpoint;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.Params;
import com.microsoft.z3.Status;
import com.microsoft.z3.Z3Exception;

public class NodFirstUnsatJob<Key, Result> extends
      BatfishJob<NodFirstUnsatResult<Key, Result>> {

   private final FirstUnsatQuerySynthesizer<Key, Result> _query;

   private final Synthesizer _synthesizer;

   public NodFirstUnsatJob(Settings settings, Synthesizer synthesizer,
         FirstUnsatQuerySynthesizer<Key, Result> query) {
      super(settings);
      _synthesizer = synthesizer;
      _query = query;
   }

   @Override
   public NodFirstUnsatResult<Key, Result> call() throws Exception {
      long startTime = System.currentTimeMillis();
      long elapsedTime;
      try (Context ctx = new Context()) {
         NodProgram baseProgram = _query.synthesizeBaseProgram(_synthesizer,
               ctx);
         NodProgram queryProgram = _query.getNodProgram(baseProgram);
         NodProgram program = baseProgram.append(queryProgram);
         Params p = ctx.mkParams();
         p.add("fixedpoint.engine", "datalog");
         p.add("fixedpoint.datalog.default_relation", "doc");
         Fixedpoint fix = ctx.mkFixedpoint();
         fix.setParameters(p);
         for (FuncDecl relationDeclaration : program.getRelationDeclarations()
               .values()) {
            fix.registerRelation(relationDeclaration);
         }
         for (BoolExpr rule : program.getRules()) {
            fix.addRule(rule, null);
         }
         Key key = _query.getKey();
         for (int queryNum = 0; queryNum < program.getQueries().size(); queryNum++) {
            BoolExpr query = program.getQueries().get(queryNum);
            Status status = fix.query(query);
            elapsedTime = System.currentTimeMillis() - startTime;
            switch (status) {
            case SATISFIABLE:
               break;
            case UNKNOWN:
               return new NodFirstUnsatResult<Key, Result>(elapsedTime,
                     _logger.getHistory(), new BatfishException(
                           "Query satisfiability unknown"));
            case UNSATISFIABLE:
               return new NodFirstUnsatResult<Key, Result>(key, queryNum,
                     _query.getResultsByQueryIndex().get(queryNum),
                     _logger.getHistory(), elapsedTime);
            default:
               return new NodFirstUnsatResult<Key, Result>(elapsedTime,
                     _logger.getHistory(), new BatfishException(
                           "invalid status"));
            }
         }
         elapsedTime = System.currentTimeMillis() - startTime;
         return new NodFirstUnsatResult<Key, Result>(key, null, null,
               _logger.getHistory(), elapsedTime);
      }
      catch (Z3Exception e) {
         elapsedTime = System.currentTimeMillis() - startTime;
         return new NodFirstUnsatResult<Key, Result>(elapsedTime,
               _logger.getHistory(), new BatfishException(
                     "Error running NoD on concatenated data plane", e));
      }
   }
}
