/**
Copyright (C) SYSTAP, LLC 2006-2015.  All rights reserved.

Contact:
     SYSTAP, LLC
     2501 Calvert ST NW #106
     Washington, DC 20008
     licenses@systap.com

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; version 2 of the License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package com.bigdata.rdf.sail.remote;

import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.query.impl.DatasetImpl;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.rio.RDFHandler;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.helpers.RDFHandlerBase;

import com.bigdata.rdf.sail.webapp.client.EncodeDecodeValue;
import com.bigdata.rdf.sail.webapp.client.MockRemoteRepository;
import com.bigdata.rdf.sail.webapp.client.RemoteRepositoryDecls;

/**
 * Test of the Java client for the REST API using mock objects to verify that
 * the generated http request is correct.
 * 
 * @author igorkim
 */
public class BigdataSailRemoteRepositoryConnectionTest extends TestCase {
	
	private BigdataSailRemoteRepositoryConnection con;
	private MockRemoteRepository remote;
	private BigdataSailRemoteRepository repo;

	private final boolean includeInferred = false;
	private final Resource s = new URIImpl(":háð");
	private final URI p = new URIImpl(":谓词");
	private final Value o = new URIImpl(":αντικείμενο");
	private final Resource c = new URIImpl(":บริบท");
	private final DatasetImpl dataset = new DatasetImpl();
	private final URI defaultGraph1 = new URIImpl(":sjálfgefiðLínurit1");
	private final URI defaultGraph2 = new URIImpl(":กราฟเริ่มต้น2");
	private final URI namedGraph1 = new URIImpl(":नाउँग्राफ1");
	private final URI namedGraph2 = new URIImpl(":命名图2");
	private final Set<URI> defaultGraphs = new HashSet<>();
	private final Set<URI> namedGraphs = new HashSet<>();

	@Before
    protected void setUp() {
		remote = MockRemoteRepository.create("s\n", "");
		repo = remote.getBigdataSailRemoteRepository();
		con = new BigdataSailRemoteRepositoryConnection(repo);
		dataset.addDefaultGraph(defaultGraph1);
		dataset.addDefaultGraph(defaultGraph2);
		defaultGraphs.add(defaultGraph1);
		defaultGraphs.add(defaultGraph2);
		dataset.addNamedGraph(namedGraph1);
		dataset.addNamedGraph(namedGraph2);
		namedGraphs.add(namedGraph1);
		namedGraphs.add(namedGraph2);
	}
	
	@After
	protected void tearDown() throws RepositoryException {
		if (con != null) {
			con.close();
			con = null;
		}
		if (repo != null) {
			repo.shutDown();
			remote = null;
		}
	}

	@Test
	public void testTupleQueryBindings() throws Exception {
		final TupleQuery tq = con.prepareTupleQuery(QueryLanguage.SPARQL, "select * where {?s ?p ?o}");
		tq.setBinding("s", s);
		tq.setBinding("p", p);
		tq.setBinding("o", o);
		{
			assertEquals(s,tq.getBindings().getBinding("s").getValue());
			assertEquals(p,tq.getBindings().getBinding("p").getValue());
			assertEquals(o,tq.getBindings().getBinding("o").getValue());
			
			tq.removeBinding("o");
			assertFalse(tq.getBindings().hasBinding("o"));
			
			TupleQueryResult tqr = tq.evaluate();
			try {
				assertEquals(EncodeDecodeValue.encodeValue(s),remote.data.opts.getRequestParam("$s"));
				assertEquals(EncodeDecodeValue.encodeValue(p),remote.data.opts.getRequestParam("$p"));
				assertEquals(null,remote.data.opts.getRequestParam("$o"));
				assertEquals(EncodeDecodeValue.encodeValue(s),remote.data.request.getParams().get("$s").getValue());
				assertEquals(EncodeDecodeValue.encodeValue(p),remote.data.request.getParams().get("$p").getValue());
				assertEquals(null,remote.data.request.getParams().get("$o"));
			} finally {
				tqr.close();
			}
			tq.clearBindings();
			assertEquals(0,tq.getBindings().size());
		}
	}

	@Test
	public void testTupleQueryIncludeInferred() throws Exception {
		final TupleQuery tq = con.prepareTupleQuery(QueryLanguage.SPARQL, "select * where {?s ?p ?o}");
		tq.setIncludeInferred(false);
		tq.evaluate();
		assertEquals("false", remote.data.opts.getRequestParam(RemoteRepositoryDecls.INCLUDE_INFERRED));
		assertEquals("false", remote.data.request.getParams().get(RemoteRepositoryDecls.INCLUDE_INFERRED).getValue());
		
		tq.setIncludeInferred(true);
		final TupleQueryResult tqr = tq.evaluate();
		try {
			assertEquals("true", remote.data.opts.getRequestParam(RemoteRepositoryDecls.INCLUDE_INFERRED));
			assertEquals("true", remote.data.request.getParams().get(RemoteRepositoryDecls.INCLUDE_INFERRED).getValue());
		} finally {
			tqr.close();
		}
	}
	
	@Test
	public void testTupleQueryDataset() throws Exception {
		final TupleQuery tq = con.prepareTupleQuery(QueryLanguage.SPARQL, "select * where {?s ?p ?o}");
		tq.setDataset(dataset);
		final TupleQueryResult tqr = tq.evaluate();
		try {
			assertEquals(defaultGraphs,tq.getDataset().getDefaultGraphs());
			assertEquals(namedGraphs,tq.getDataset().getNamedGraphs());
			// TODO support multiple default graphs and multiple named graphs
			// Note that com.bigdata.rdf.sail.webapp.client.AbstractConnectOptions.getRequestParam(String)
			// returns only first value for the specified name, thus only one default graph and only one named graph could be tested
			assertEquals(defaultGraph1.stringValue(),remote.data.opts.getRequestParam(RemoteRepositoryDecls.DEFAULT_GRAPH_URI));
			assertEquals(defaultGraph1.stringValue(),remote.data.request.getParams().get(RemoteRepositoryDecls.DEFAULT_GRAPH_URI).getValue());
			assertEquals(namedGraph1.stringValue(),remote.data.opts.getRequestParam(RemoteRepositoryDecls.NAMED_GRAPH_URI));
			assertEquals(namedGraph1.stringValue(),remote.data.request.getParams().get(RemoteRepositoryDecls.NAMED_GRAPH_URI).getValue());
		} finally {
			tqr.close();
		}
	}

	@Test
	public void testTupleQueryBaseURI() throws Exception {
	    final String baseURI = ":baseURI";
	    final TupleQuery tq = con.prepareTupleQuery(QueryLanguage.SPARQL, "select * where {?s ?p ?o}", baseURI);
	    final TupleQueryResult tqr = tq.evaluate();
		try {
			assertEquals(baseURI, remote.data.opts.getRequestParam(RemoteRepositoryDecls.BASE_URI));
			assertEquals(baseURI,remote.data.opts.getRequestParam(RemoteRepositoryDecls.BASE_URI));
		} finally {
			tqr.close();
		}
	}

	@Test
	public void testGetStatements() throws RepositoryException {
		final RepositoryResult<Statement> stmts = con.getStatements(s, p, o, includeInferred, c);
		try {
			assertEquals(EncodeDecodeValue.encodeValue(s), remote.data.opts.getRequestParam("s"));
			assertEquals(EncodeDecodeValue.encodeValue(p), remote.data.opts.getRequestParam("p"));
			assertEquals(EncodeDecodeValue.encodeValue(o), remote.data.opts.getRequestParam("o"));
			assertEquals(EncodeDecodeValue.encodeValue(c), remote.data.opts.getRequestParam("c"));
			assertEquals(Boolean.toString(includeInferred), remote.data.opts.getRequestParam(RemoteRepositoryDecls.INCLUDE_INFERRED));
		} finally {
			stmts.close();
		}
	}
	
	@Test
	public void testExport() throws RepositoryException, RDFHandlerException {
		final RDFHandler handler = new RDFHandlerBase();
		con.exportStatements(s, p, o, includeInferred, handler, c);
		assertEquals(EncodeDecodeValue.encodeValue(s), remote.data.opts.getRequestParam("s"));
		assertEquals(EncodeDecodeValue.encodeValue(p), remote.data.opts.getRequestParam("p"));
		assertEquals(EncodeDecodeValue.encodeValue(o), remote.data.opts.getRequestParam("o"));
		assertEquals(EncodeDecodeValue.encodeValue(c), remote.data.opts.getRequestParam("c"));
		assertEquals(Boolean.toString(includeInferred), remote.data.opts.getRequestParam(RemoteRepositoryDecls.INCLUDE_INFERRED));
	}
	
}
