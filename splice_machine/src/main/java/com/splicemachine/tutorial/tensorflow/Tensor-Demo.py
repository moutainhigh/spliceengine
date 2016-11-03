
# Copyright 2016 The TensorFlow Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ==============================================================================
"""Example code for TensorFlow Wide & Deep Tutorial using TF.Learn API."""
from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import sys
if sys.version_info[0] < 3:
    from StringIO import StringIO
else:
    from io import StringIO

import json
import tempfile
from six.moves import urllib

import pandas as pd
import tensorflow as tf


# In[3]:

# This will error if you run more than once

flags = tf.app.flags
FLAGS = flags.FLAGS

flags.DEFINE_string("model_dir", "", "Base directory for output models.")
flags.DEFINE_string("model_type", "wide_n_deep",
                    "Valid model types: {'wide', 'deep', 'wide_n_deep'}.")
flags.DEFINE_integer("train_steps", 200, "Number of training steps.")
flags.DEFINE_string(
    "train_data",
    "",
    "Path to the training data.")
flags.DEFINE_string(
    "test_data",
    "",
    "Path to the test data.")
flags.DEFINE_string(
    "inputs",
    '{"columns": ["age","workclass","fnlwgt","education","education_num","marital_status","occupation","relationship","race","gender","capital_gain","capital_loss","hours_per_week","native_country","income_bracket"],"categorical_columns": ["workclass","education","marital_status","occupation","relationship","race","gender","native_country"],"continuous_columns": ["age","education_num","capital_gain","capital_loss","hours_per_week"],"label_column": "label","bucketized_columns": {"age_buckets": {      "age": [    18,    25,    30,    35,    40,    45,    50,    55,    60,    65  ]}},"crossed_columns": [[  "education",  "occupation"],[  "age_buckets",  "education",  "occupation"],[  "native_country",  "occupation"]],"train_data_path": "/Users/erindriggers/anaconda/envs/tensorflow/projects/wide_n_deep/data/train/part-r-00000.csv","test_data_path": "/Users/erindriggers/anaconda/envs/tensorflow/projects/wide_n_deep/data/test/part-r-00000.csv"}',
    "Input data dictionary")
flags.DEFINE_string("input_record", "","Comma delimited input record")
flags.DEFINE_string("predict", "false","Indicates if we are predicting or building the model")



# In[4]:


## TBD: The dict below should be input to teh script and constructed by a stored procedure
## The Stored Procedure can construct JSON and then this script can decode the JSON into the dict
## The paths to the files below should be paths constructed by a Splice Machine Export


INPUT_DICT=json.loads(FLAGS.inputs)
COLUMNS = INPUT_DICT['columns'];
LABEL_COLUMN = INPUT_DICT['label_column'];
CATEGORICAL_COLUMNS = INPUT_DICT['categorical_columns'];
CONTINUOUS_COLUMNS = INPUT_DICT['continuous_columns'];
CROSSED_COLUMNS = INPUT_DICT['crossed_columns'];
BUCKETIZED_COLUMNS = INPUT_DICT['bucketized_columns'];

print("INPUT_DICT=%s" % INPUT_DICT)
print("COLUMNS=%s" % COLUMNS)
print("CATEGORICAL_COLUMNS=%s" % CATEGORICAL_COLUMNS)
print("CONTINUOUS_COLUMNS=%s" % CONTINUOUS_COLUMNS)
print("LABEL_COLUMN=%s" % LABEL_COLUMN)
print("COLUMNS=%s" % COLUMNS)
print("BUCKETIZED_COLUMNS=%s" % BUCKETIZED_COLUMNS)
print("CROSSED_COLUMNS=%s" % CROSSED_COLUMNS)


# In[5]:


def maybe_download():
  """May be downloads training data and returns train and test file names."""
  if FLAGS.train_data:
    train_file_name = FLAGS.train_data
  else:
    train_file = tempfile.NamedTemporaryFile(delete=False)
    urllib.request.urlretrieve(INPUT_DICT['train_data_path'], train_file.name)  # pylint: disable=line-too-long
    train_file_name = train_file.name
    train_file.close()
    print("Training data is downloaded to %s" % train_file_name)

  if FLAGS.test_data:
    test_file_name = FLAGS.test_data
  else:
    test_file = tempfile.NamedTemporaryFile(delete=False)
    urllib.request.urlretrieve(INPUT_DICT['test_data_path'], test_file.name)  # pylint: disable=line-too-long
    test_file_name = test_file.name
    test_file.close()
    print("Test data is downloaded to %s" % test_file_name)

  return train_file_name, test_file_name


# In[6]:

def prepare_sparse_columns(cols):
    """Creates tf sparse columns with hash buckets"""
    # Sparse base columns.
    # TBD: allow keyed columns and hash bucket size as input
    tf_cols ={}
    for col in cols :
        tf_cols[col] = tf.contrib.layers.sparse_column_with_hash_bucket(
          col, hash_bucket_size=1000)
    return tf_cols


# In[7]:

SPARSE_TF_COLUMNS = prepare_sparse_columns(CATEGORICAL_COLUMNS)
print(SPARSE_TF_COLUMNS)


# In[8]:

def prepare_continuous_columns(cols):
    """Creates tf.contrib.layers.real_valued_columns"""
    #Continuous base columns
    tf_cols ={}
    for col in cols :
        tf_cols[col] = (tf.contrib.layers.real_valued_column(col))
    return tf_cols


# In[9]:

REAL_TF_COLUMNS = prepare_continuous_columns(CONTINUOUS_COLUMNS)
print(REAL_TF_COLUMNS)


# In[10]:

def prepare_buckets(cols):
    """Creates tf bucketed columns"""
    new_cols = {}
    for newCol in cols:
        keyvalues = cols[newCol]
        for colname in keyvalues:
            orig_col = REAL_TF_COLUMNS[colname]
            bound = keyvalues[colname]
            new_cols[newCol] = tf.contrib.layers.bucketized_column(orig_col, boundaries=bound)
    return new_cols


# In[11]:

print(BUCKETIZED_COLUMNS)
BUCKETIZED_TF_COLUMNS = prepare_buckets(BUCKETIZED_COLUMNS)


# In[12]:

def prepare_embedded_columns(cols):
    """Create tf.contrib.layers.embedding_columns for the sparse entries"""
    tf_cols = {}
    for col in cols:
        tf_cols[col] = tf.contrib.layers.embedding_column(col, dimension=8)
    return tf_cols


# In[13]:

print(list(SPARSE_TF_COLUMNS.keys()))


# In[14]:

EMBEDDED_TF_COLUMNS = prepare_embedded_columns(list(SPARSE_TF_COLUMNS.values()))


# In[15]:

print(EMBEDDED_TF_COLUMNS)


# In[16]:

DEEP_TF_COLUMNS =  list(EMBEDDED_TF_COLUMNS.values()) + list(REAL_TF_COLUMNS.values())
print(DEEP_TF_COLUMNS)


# In[18]:

def prepare_crossed(cols):
    """Creates tf crossed columns"""
    new_cols = [];
    for tuple in cols:
        list_of_cols = []
        for var in tuple:
            b = BUCKETIZED_TF_COLUMNS.get(var,False)
            s = SPARSE_TF_COLUMNS.get(var,False)
            r = REAL_TF_COLUMNS.get(var,False)
            if b : tf_var = b
            else :
                if s : tf_var = s
                else :
                    if r : tf_var = r
            print(tf_var)
            list_of_cols.append(tf_var)
        new_cols.append(tf.contrib.layers.crossed_column(list_of_cols,
                      hash_bucket_size=int(1e6)))
    return new_cols


# In[19]:

CROSSED_TF_COLS = prepare_crossed(CROSSED_COLUMNS)


# In[20]:

print(CROSSED_TF_COLS)
CROSSED_TF_COLS[1]

# In[17]:

WIDE_TF_COLUMNS = list(SPARSE_TF_COLUMNS.values()) + list(BUCKETIZED_TF_COLUMNS.values()) + list(CROSSED_TF_COLS)
print(WIDE_TF_COLUMNS)


# In[21]:

def build_estimator(model_dir):
  """Build an estimator."""
  m = tf.contrib.learn.DNNLinearCombinedClassifier(
    model_dir=model_dir,
    linear_feature_columns=WIDE_TF_COLUMNS,
    dnn_feature_columns=DEEP_TF_COLUMNS,
    dnn_hidden_units=[100, 50])
  return m


# In[22]:

def input_fn(df):
  """Input builder function."""
  # Creates a dictionary mapping from each continuous feature column name (k) to
  # the values of that column stored in a constant Tensor.
  continuous_cols = {k: tf.constant(df[k].values) for k in CONTINUOUS_COLUMNS}
  # Creates a dictionary mapping from each categorical feature column name (k)
  # to the values of that column stored in a tf.SparseTensor.
  categorical_cols = {k: tf.SparseTensor(
      indices=[[i, 0] for i in range(df[k].size)],
      values=df[k].values,
      shape=[df[k].size, 1])
                      for k in CATEGORICAL_COLUMNS}
  # Merges the two dictionaries into one.
  feature_cols = dict(continuous_cols)
  feature_cols.update(categorical_cols)
  # Converts the label column into a constant Tensor.
  label = tf.constant(df[LABEL_COLUMN].values)
  # Returns the feature columns and the label.
  return feature_cols, label


# In[25]:

def train_and_eval():
  """Train and evaluate the model."""
#  train_file_name, test_file_name = maybe_download()
  train_file_name=INPUT_DICT['train_data_path'];
  test_file_name=INPUT_DICT['test_data_path'];

  df_train = pd.read_csv(
      tf.gfile.Open(train_file_name),
      names=COLUMNS,
      skipinitialspace=True,
      engine="python")
  df_test = pd.read_csv(
      tf.gfile.Open(test_file_name),
      names=COLUMNS,
      skipinitialspace=True,
      skiprows=1,
      engine="python")
    
# Temp hack - label should come in the input 
  df_train[LABEL_COLUMN] = (
      df_train["income_bracket"].apply(lambda x: ">50K" in x)).astype(int)
  df_test[LABEL_COLUMN] = (
      df_test["income_bracket"].apply(lambda x: ">50K" in x)).astype(int)


  model_dir = tempfile.mkdtemp() if not FLAGS.model_dir else FLAGS.model_dir
  print("model directory = %s" % model_dir)

  m = build_estimator(model_dir)
  m.fit(input_fn=lambda: input_fn(df_train), steps=FLAGS.train_steps)
  results = m.evaluate(input_fn=lambda: input_fn(df_test), steps=1)
  for key in sorted(results):
    print("%s: %s" % (key, results[key]))

def predict_outcome():
  model_dir = tempfile.mkdtemp() if not FLAGS.model_dir else FLAGS.model_dir
  print('model_dir = %s' % model_dir);
  m = build_estimator(model_dir)

  indata=StringIO(FLAGS.input_record)

  prediction_set = pd.read_csv(
      indata,
      names=COLUMNS,
      skipinitialspace=True,
      skiprows=0,
      engine="python")

  prediction_set[LABEL_COLUMN] = (
      prediction_set["income_bracket"].apply(lambda x: ">50K" in x)).astype(int)


  y=m.predict(input_fn=lambda: input_fn(prediction_set))
  print('Predictions: {}'.format(str(y)))
  return y

# In[ ]:

def main(_):
  if FLAGS.predict == "true":
    predict_outcome()
  else:
    train_and_eval()

if __name__ == "__main__":
  tf.app.run()


