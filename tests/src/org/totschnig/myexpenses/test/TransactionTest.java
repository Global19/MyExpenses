/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.totschnig.myexpenses.test;

import java.util.Currency;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.activity.MyExpenses;
import org.totschnig.myexpenses.model.Account;
import org.totschnig.myexpenses.model.Money;
import org.totschnig.myexpenses.model.Template;
import org.totschnig.myexpenses.model.Transaction;
import org.totschnig.myexpenses.model.Transfer;

import android.test.AndroidTestCase;

import junit.framework.Assert;

public class TransactionTest extends AndroidTestCase {
  private Currency currency;
  private Account mAccount1;
  private Account mAccount2;
  private static final String TEST_ID = "functest";
  private MyApplication app;
  
  @Override
  protected void setUp() throws Exception {
      super.setUp();
      app = (MyApplication) getContext().getApplicationContext();
      app.setDatabaseName(TEST_ID);
      mAccount1 = new Account("TestAccount 1",100,"Main account");
      mAccount1.save();
      mAccount2 = new Account("TestAccount 2",100,"Secondary account");
      mAccount2.save();
  }
  public void testAA_Transaction() {
    Assert.assertEquals(0, Transaction.getTransactionSequence());
    Transaction op1 = Transaction.getTypedNewInstance(MyExpenses.TYPE_TRANSACTION,mAccount1.id);
    op1.amount = new Money(currency,100L);
    op1.comment = "test transfer";
    long id = Long.valueOf(op1.save().getLastPathSegment());
    Assert.assertEquals(1, Transaction.getTransactionSequence());
    Transaction.delete(id);
    Assert.assertEquals(1, Transaction.getTransactionSequence());
  }
  
  public void testTransfer() {
    Transfer op = (Transfer) Transaction.getTypedNewInstance(MyExpenses.TYPE_TRANSFER,mAccount1.id);
    op.catId = mAccount2.id;
    op.amount = new Money(currency,(long) 100);
    op.comment = "test transfer";
    op.save();
    Assert.assertTrue(op.id > 0);
    Transfer peer = (Transfer) Transaction.getInstanceFromDb(op.transfer_peer);
    Assert.assertEquals(op.id, peer.transfer_peer);
  }
  public void testTemplate() {
    Long start = mAccount1.getCurrentBalance().getAmountMinor();
    Long amount = (long) 100;
    Transaction op1 = Transaction.getTypedNewInstance(MyExpenses.TYPE_TRANSACTION,mAccount1.id);
    op1.amount = new Money(currency,amount);
    op1.comment = "test transfer";
    op1.save();
    Assert.assertEquals(mAccount1.getCurrentBalance().getAmountMinor().longValue(), start+amount);
    Template t = new Template(op1,"Template");
    t.save();
    Transaction op2  = Transaction.getInstanceFromTemplate(t.id);
    op2.save();
    Assert.assertEquals(mAccount1.getCurrentBalance().getAmountMinor().longValue(), start+2*amount);
  }
  @Override
  protected void tearDown() throws Exception {
    Account.delete(mAccount1.id);
    Account.delete(mAccount2.id);
  }
}
