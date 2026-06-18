import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getClassrooms, createClassroom, updateClassroom } from '../api/classrooms';
import { toast } from '../utils/toast.jsx';

export function useClassrooms({ search, page = 0, size = 20, sort, direction } = {}) {
  const queryClient = useQueryClient();

  const { data: pageData, isLoading: loading } = useQuery({
    queryKey: ['classrooms', 'list', { search, page, size, sort, direction }],
    queryFn: () => getClassrooms({ search, page, size, sort, direction }),
    keepPreviousData: true,
  });

  const classrooms = pageData?.items ?? [];
  const totalElements = pageData?.totalElements ?? 0;
  const totalPages = pageData?.totalPages ?? 1;

  const createMutation = useMutation({
    mutationFn: createClassroom,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['classrooms'] });
      toast.success('Aula creada correctamente.');
    },
    onError: (err) => toast.error(err.message),
  });

  const updateMutation = useMutation({
    mutationFn: ({ uuid, payload }) => updateClassroom(uuid, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['classrooms'] });
      toast.success('Aula actualizada correctamente.');
    },
    onError: (err) => toast.error(err.message),
  });

  return {
    classrooms,
    totalElements,
    totalPages,
    loading,
    createMutation,
    updateMutation,
  };
}

