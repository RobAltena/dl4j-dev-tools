import numpy as np
import tensorflow as tf
from tfoptests.persistor import TensorFlowPersistor
from tfoptests.test_graph import TestGraph


class MathOpsOne(TestGraph):
    def __init__(self, *args, **kwargs):
        super(MathOpsOne, self).__init__(*args, **kwargs)
        self.input_0 = np.random.uniform(size=(3, 3))
        self.input_1 = np.random.uniform(size=(3, 3)) + np.random.uniform(size=(3, 3))

    def list_inputs(self):
        return ["input_0", "input_1"]

    def get_placeholder_input(self, name):
        if name == "input_0":
            return self.input_0
        if name == "input_1":
            return self.input_1

    def _get_placeholder_shape(self, name):
        if name == "input_0" or name == "input_1":
            return [3, 3]


def test_mathops_one():
    mathops_1 = MathOpsOne(seed=19)
    in_node_0 = mathops_1.get_placeholder("input_0")
    in_node_1 = mathops_1.get_placeholder("input_1")
    n0 = tf.add(np.arange(-4., 5., 1.).astype(np.float64).reshape(3, 3), in_node_0)
    n1 = tf.abs(n0)
    n3 = tf.add(n1, tf.Variable(tf.random_normal([3, 3], dtype=tf.float64)))
    n4 = tf.floordiv(n3, in_node_1)
    out_node = tf.tanh(n4, name="output")

    placeholders = [in_node_0, in_node_1]
    predictions = [out_node]
    # Run and persist
    tfp = TensorFlowPersistor(save_dir="g_01")
    tfp.set_placeholders(placeholders) \
        .set_output_tensors(predictions) \
        .set_test_data(mathops_1.get_test_data()) \
        .build_save_frozen_graph()


if __name__ == '__main__':
    test_mathops_one()
